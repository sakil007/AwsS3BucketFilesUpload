package com.vijay.jsonwizard.presenters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TimePicker;
import android.widget.Toast;

import com.rengwuxian.materialedittext.MaterialEditText;
import com.vijay.jsonwizard.R;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.customviews.CheckBox;
import com.vijay.jsonwizard.customviews.RadioButton;
import com.vijay.jsonwizard.fragments.JsonFormFragment;
import com.vijay.jsonwizard.interactors.JsonFormInteractor;
import com.vijay.jsonwizard.mvp.MvpBasePresenter;
import com.vijay.jsonwizard.utils.ImageUtils;
import com.vijay.jsonwizard.utils.ValidationStatus;
import com.vijay.jsonwizard.views.JsonFormFragmentView;
import com.vijay.jsonwizard.viewstates.JsonFormFragmentViewState;
import com.vijay.jsonwizard.widgets.EditTextFactory;
import com.vijay.jsonwizard.widgets.ImagePickerFactory;
import com.vijay.jsonwizard.widgets.SpinnerFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.ganfra.materialspinner.MaterialSpinner;

import static com.vijay.jsonwizard.utils.FormUtils.dpToPixels;

/**
 * Created by vijay on 5/14/15.
 */
public class JsonFormFragmentPresenter extends MvpBasePresenter<JsonFormFragmentView<JsonFormFragmentViewState>> {
    private static final String TAG = "FormFragmentPresenter";
    private static final int RESULT_LOAD_IMG = 1;
    private static final int RESULT_LOAD_IMG_fromCamera = 2;
    private String mStepName;
    private JSONObject mStepDetails;
    private String mCurrentKey;
    private JsonFormInteractor mJsonFormInteractor = JsonFormInteractor.getInstance();
    final Calendar myCalendar = Calendar.getInstance();
    private Uri uriSavedImage;

    public void addFormElements() {
        mStepName = getView().getArguments().getString("stepName");
        JSONObject step = getView().getStep(mStepName);
        try {
            mStepDetails = new JSONObject(step.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        List<View> views = mJsonFormInteractor.fetchFormElements(mStepName, getView().getContext(), mStepDetails,
                getView().getCommonListener());
        getView().addFormElements(views);
    }

    @SuppressLint("ResourceAsColor")
    public void setUpToolBar() {
        if (!mStepName.equals(JsonFormConstants.FIRST_STEP_NAME)) {
            getView().setUpBackButton();
        }
        getView().setActionBarTitle(mStepDetails.optString("title"));
        if (mStepDetails.has("next")) {
            getView().updateVisibilityOfNextAndSave(true, false);
        } else {
            getView().updateVisibilityOfNextAndSave(false, true);
        }
        getView().setToolbarTitleColor(R.color.white);
    }

    public void onBackClick() {
        getView().hideKeyBoard();
        getView().backClick();
    }

    public void onNextClick(LinearLayout mainView) {
        ValidationStatus validationStatus = writeValuesAndValidate(mainView);
        if (validationStatus.isValid()) {
            JsonFormFragment next = JsonFormFragment.getFormFragment(mStepDetails.optString("next"));
            getView().hideKeyBoard();
            getView().transactThis(next);
        } else {
            getView().showToast(validationStatus.getErrorMessage());
        }
    }

    public ValidationStatus writeValuesAndValidate(LinearLayout mainView) {
        int childCount = mainView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childAt = mainView.getChildAt(i);
            String key = (String) childAt.getTag(R.id.key);
            if (childAt instanceof MaterialEditText) {
                MaterialEditText editText = (MaterialEditText) childAt;
                ValidationStatus validationStatus = EditTextFactory.validate(editText);
                if (!validationStatus.isValid()) {
                    return validationStatus;
                }
                getView().writeValue(mStepName, key, editText.getText().toString());
            } else if (childAt instanceof ImageView) {
                ValidationStatus validationStatus = ImagePickerFactory.validate((ImageView) childAt);
                if (!validationStatus.isValid()) {
                    return validationStatus;
                }
                Object path = childAt.getTag(R.id.imagePath);
                if (path instanceof String) {
                    getView().writeValue(mStepName, key, (String) path);
                }
            } else if (childAt instanceof CheckBox) {
                String parentKey = (String) childAt.getTag(R.id.key);
                String childKey = (String) childAt.getTag(R.id.childKey);
                getView().writeValue(mStepName, parentKey, JsonFormConstants.OPTIONS_FIELD_NAME, childKey,
                        String.valueOf(((CheckBox) childAt).isChecked()));
            } else if (childAt instanceof RadioButton) {
                String parentKey = (String) childAt.getTag(R.id.key);
                String childKey = (String) childAt.getTag(R.id.childKey);
                if (((RadioButton) childAt).isChecked()) {
                    getView().writeValue(mStepName, parentKey, childKey);
                }
            } else if (childAt instanceof MaterialSpinner) {
                MaterialSpinner spinner = (MaterialSpinner) childAt;
                ValidationStatus validationStatus = SpinnerFactory.validate(spinner);
                if (!validationStatus.isValid()) {
                    spinner.setError(validationStatus.getErrorMessage());
                    return validationStatus;
                } else {
                    spinner.setError(null);
                }
            }
        }
        return new ValidationStatus(true, null);
    }

    public void onSaveClick(LinearLayout mainView) {
        ValidationStatus validationStatus = writeValuesAndValidate(mainView);
        if (validationStatus.isValid()) {
            Intent returnIntent = new Intent();
            returnIntent.putExtra("json", getView().getCurrentJsonState());
            getView().finishWithResult(returnIntent);
        } else {
            Toast.makeText(getView().getContext(), validationStatus.getErrorMessage(), Toast.LENGTH_LONG);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_LOAD_IMG && resultCode == Activity.RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            // No need for null check on cursor
            Cursor cursor = getView().getContext().getContentResolver()
                    .query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String imagePath = cursor.getString(columnIndex);
            getView().updateRelevantImageView(ImageUtils.loadBitmapFromFile(imagePath, ImageUtils.getDeviceWidth(getView().getContext()), dpToPixels(getView().getContext(), 200)), imagePath, mCurrentKey);
            cursor.close();
        }
        if (requestCode == RESULT_LOAD_IMG_fromCamera && resultCode == Activity.RESULT_OK && null != data) {
            Uri selectedImage = uriSavedImage;
            String imagePath = selectedImage.getPath();
            getView().updateRelevantImageView(ImageUtils.loadBitmapFromFile(imagePath, ImageUtils.getDeviceWidth(getView().getContext()), dpToPixels(getView().getContext(), 200)), imagePath, mCurrentKey);
           // cursor.close();
        }
    }
    private void imageSelection(String key) {
        mCurrentKey = key;
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        //final CharSequence[] options = {"Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getView().getContext());
        builder.setTitle("Add Photo!");
        builder.setCancelable(false);
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (options[item].equals("Take Photo")) {
                    openImageChooser();
                } else if (options[item].equals("Choose from Gallery")) {
                    dialog.dismiss();

                    Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    getView().startActivityForResult(galleryIntent, RESULT_LOAD_IMG);

                } else if (options[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    public void onClick(View v, Context context) {
        String key = (String) v.getTag(R.id.key);
        String type = (String) v.getTag(R.id.type);
        mCurrentKey = key;
        if (JsonFormConstants.CHOOSE_IMAGE.equals(type)) {
            getView().hideKeyBoard();
            imageSelection(mCurrentKey);

           /* Intent galleryIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            mCurrentKey = key;
            getView().startActivityForResult(galleryIntent, RESULT_LOAD_IMG);*/
        }
        else if (JsonFormConstants.CHOOSE_DATEPICKER.equals(type)){
            getView().hideKeyBoard();
            DatePickerDialog mDatePickerDialog = new DatePickerDialog(context, date, myCalendar
                    .get(Calendar.YEAR), myCalendar.get(Calendar.MONTH),
                    myCalendar.get(Calendar.DAY_OF_MONTH));
            mDatePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            mDatePickerDialog.show();

        } else if (JsonFormConstants.CHOOSE_TIMEPICKER.equals(type)){
            getView().hideKeyBoard();
            showTimePiker(context);
        }
    }
    void openImageChooser() {

        File imagesFolder = new File(Environment.getExternalStorageDirectory(), "UltraMax");
        // <----
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs();
        }
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmm").format(new Date());
        File mediaFile;
        String mImageName = "Steelman_" + timeStamp + ".jpg";
        mediaFile = new File(imagesFolder.getPath() + File.separator + mImageName);
         uriSavedImage = Uri.fromFile(mediaFile);
        Log.e("TAG", uriSavedImage.getPath());
        /*Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);*/

        //= new Intent(android.provider.MediaStore.ACTION_
        // IMAGE_CAPTURE);
        //  startActivityForResult(camera, 1);


        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
        } else {
            File file = new File(uriSavedImage.getPath());
            Uri photoUri = FileProvider.getUriForFile(getView().getContext(), getView().getContext().getPackageName() + ".provider", file);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
       /* if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT", PICK_IMAGE_CAMERA);
        } else {
            intent.putExtra("android.intent.extras.CAMERA_FACING", PICK_IMAGE_CAMERA);
        }*/
        if (intent.resolveActivity(getView().getContext().getPackageManager()) != null) {
            getView().startActivityForResult(intent, RESULT_LOAD_IMG_fromCamera);
        }
    }






    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (compoundButton instanceof CheckBox) {
            String parentKey = (String) compoundButton.getTag(R.id.key);
            String childKey = (String) compoundButton.getTag(R.id.childKey);
            getView().writeValue(mStepName, parentKey, JsonFormConstants.OPTIONS_FIELD_NAME, childKey,
                    String.valueOf(((CheckBox) compoundButton).isChecked()));
        } else if (compoundButton instanceof RadioButton) {
            if (isChecked) {
                String parentKey = (String) compoundButton.getTag(R.id.key);
                String childKey = (String) compoundButton.getTag(R.id.childKey);
                getView().unCheckAllExcept(parentKey, childKey);
                getView().writeValue(mStepName, parentKey, childKey);
            }
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String parentKey = (String) parent.getTag(R.id.key);
        if (position >= 0) {
            String value = (String) parent.getItemAtPosition(position);
            getView().writeValue(mStepName, parentKey, value);
        }
    }


    public static String getCalenderPikertoDate(Date date){
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.US);
        return sdf.format(date);
    }
    DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {

        @Override
        public void onDateSet(DatePicker view, int year, int monthOfYear,
                              int dayOfMonth) {
            // TODO Auto-generated method stub
            myCalendar.set(Calendar.YEAR, year);
            myCalendar.set(Calendar.MONTH, monthOfYear);
            myCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

           // String parentKey = (String) view.getTag(R.id.key);
                String value=getCalenderPikertoDate(myCalendar.getTime());
            getView().updateDatePicker(value,mCurrentKey);

           // updateLabel();
        }

    };

    public void showTimePiker(Context context){
        Calendar mcurrentTime = Calendar.getInstance();
        int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
        int minute = mcurrentTime.get(Calendar.MINUTE);
        TimePickerDialog mTimePicker;
        mTimePicker = new TimePickerDialog(context, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                String value=String.format("%02d:%02d", selectedHour, selectedMinute);
                getView().updateDatePicker(value,mCurrentKey);
            }
        }, hour, minute, true);//Yes 24 hour time
        mTimePicker.setTitle("Select Time");
        mTimePicker.show();
    }

      /* edittext.setOnClickListener(new OnClickListener() {

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            new DatePickerDialog(classname.this, date, myCalendar
                    .get(Calendar.YEAR), myCalendar.get(Calendar.MONTH),
                    myCalendar.get(Calendar.DAY_OF_MONTH)).show();
        }
    });*/



}
