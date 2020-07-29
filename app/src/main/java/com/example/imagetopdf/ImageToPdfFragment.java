package com.example.imagetopdf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import android.os.Parcelable;
import android.preference.PreferenceManager;


import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.afollestad.materialdialogs.MaterialDialog;

import com.theartofdev.edmodo.cropper.CropImage;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.engine.impl.PicassoEngine;
import com.zhihu.matisse.internal.entity.CaptureStrategy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.example.imagetopdf.Constants.DEFAULT_PAGE_COLOR;
import static com.example.imagetopdf.Constants.IMAGE_SCALE_TYPE_ASPECT_RATIO;
import static com.example.imagetopdf.Constants.MASTER_PWD_STRING;
import static com.example.imagetopdf.Constants.OPEN_SELECT_IMAGES;
import static com.example.imagetopdf.Constants.READ_WRITE_CAMERA_PERMISSIONS;
import static com.example.imagetopdf.Constants.RESULT;
import static com.example.imagetopdf.Constants.STORAGE_LOCATION;


/**
 * ImageToPdfFragment fragment to start with creating PDF
 */
public class ImageToPdfFragment extends Fragment implements
        OnPDFCreatedInterface {

    private static final int INTENT_REQUEST_APPLY_FILTER = 10;
    private static final int INTENT_REQUEST_PREVIEW_IMAGE = 11;
    private static final int INTENT_REQUEST_REARRANGE_IMAGE = 12;
    private static final int INTENT_REQUEST_GET_IMAGES = 13;
    private static final int REQUEST_PERMISSIONS_CODE = 124;

    @BindView(R.id.pdfCreate)
    Button mCreatePdf;
    @BindView(R.id.pdfOpen)
    Button mOpenPdf;

    @BindView(R.id.tvNoOfImages)
    TextView mNoOfImages;

    private Activity mActivity;
    public static ArrayList<String> mImagesUri = new ArrayList<>();
    private static final ArrayList<String> mUnarrangedImagesUri = new ArrayList<>();
    private String mPath;
    private SharedPreferences mSharedPreferences;
    private FileUtils mFileUtils;
    private PageSizeUtils mPageSizeUtils;
    private int mPageColor;
    private boolean mIsButtonAlreadyClicked = false;
    private ImageToPDFOptions mPdfOptions;
    private MaterialDialog mMaterialDialog;
    private String mHomePath;
    private int mMarginTop = 50;
    private int mMarginBottom = 38;
    private int mMarginLeft = 50;
    private int mMarginRight = 38;
    private String mPageNumStyle;
    private int mChoseId;
    private boolean mPermissionGranted = false;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (Activity) context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_images_to_pdf, container, false);
        ButterKnife.bind(this, root);

        // Initialize variables
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        mPermissionGranted = PermissionsUtils.getInstance().checkRuntimePermissions(this,
                READ_WRITE_CAMERA_PERMISSIONS);
        mFileUtils = new FileUtils(mActivity);
        mPageSizeUtils = new PageSizeUtils(mActivity);
        mPageColor = mSharedPreferences.getInt(Constants.DEFAULT_PAGE_COLOR_ITP,
                DEFAULT_PAGE_COLOR);
        mHomePath = mSharedPreferences.getString(STORAGE_LOCATION,
                StringUtils.getInstance().getDefaultStorageLocation());

        // Get default values & show enhancement options
        resetValues();

        // Check for the images received
        checkForImagesInBundle();

        if (mImagesUri.size() > 0) {
            mNoOfImages.setText(String.format(mActivity.getResources()
                    .getString(R.string.images_selected), mImagesUri.size()));
            mNoOfImages.setVisibility(View.VISIBLE);
            mCreatePdf.setEnabled(true);
            StringUtils.getInstance().showSnackbar(mActivity, R.string.successToast);
        } else {
            mNoOfImages.setVisibility(View.GONE);
        }

        return root;
    }

    /**
     * Adds images (if any) received in the bundle
     */
    private void checkForImagesInBundle() {
        Bundle bundle = getArguments();
        if (bundle != null) {
            if (bundle.getBoolean(OPEN_SELECT_IMAGES))
                startAddingImages();
            ArrayList<Parcelable> uris = bundle.getParcelableArrayList(getString(R.string.bundleKey));
            if (uris == null)
                return;
            for (Parcelable p : uris) {
                Uri uri = (Uri) p;
                if (mFileUtils.getUriRealPath(uri) == null) {
                    StringUtils.getInstance().showSnackbar(mActivity, R.string.whatsappToast);
                } else {
                    mImagesUri.add(mFileUtils.getUriRealPath(uri));
                }
            }
        }
    }


    /**
     * Adding Images to PDF
     */
    @OnClick(R.id.addImages)
    void startAddingImages() {
        if (!mPermissionGranted) {
            getRuntimePermissions();
            return;
        }
        if (!mIsButtonAlreadyClicked) {
            selectImages();
            mIsButtonAlreadyClicked = true;
        }
    }

    /**
     * Create Pdf of selected images
     */
    @OnClick(R.id.pdfCreate)
    void pdfCreateClicked() {
        createPdf(false);
    }



    private String TAG="namnx";
    //todo convert image to pdf
    private void createPdf(boolean isGrayScale) {
        addPageNumbers();
        addMargins();
        Log.i(TAG, "createPdf: "+isGrayScale);
        mPdfOptions.setImagesUri(mImagesUri);
        mPdfOptions.setPageSize(PageSizeUtils.mPageSize);
        mPdfOptions.setImageScaleType(ImageUtils.getInstance().mImageScaleType);
        mPdfOptions.setPageNumStyle(mPageNumStyle);
        mPdfOptions.setMasterPwd(mSharedPreferences.getString(MASTER_PWD_STRING, Constants.appName));
        mPdfOptions.setPageColor(mPageColor);

        String preFillName = mFileUtils.getLastFileName(mImagesUri);

        MaterialDialog.Builder builder = DialogUtils.getInstance().createCustomDialog(mActivity,
                R.string.creating_pdf, R.string.enter_file_name);
        builder.input(getString(R.string.example), preFillName, (dialog, input) -> {
            if (StringUtils.getInstance().isEmpty(input)) {
                StringUtils.getInstance().showSnackbar(mActivity, R.string.snackbar_name_not_blank);
            } else {
                final String filename = input.toString();
                FileUtils utils = new FileUtils(mActivity);
                if (!utils.isFileExist(filename + getString(R.string.pdf_ext))) {
                    Log.i(TAG, "createPdf: vao 1");
                    mPdfOptions.setOutFileName(filename);
                    if (isGrayScale)
                        saveImagesInGrayScale();

                    new CreatePdf(mPdfOptions, mHomePath,
                            ImageToPdfFragment.this).execute();
                } else {
                    Log.i(TAG, "createPdf: vao 2");
                    MaterialDialog.Builder builder2 = DialogUtils.getInstance().createOverwriteDialog(mActivity);
                    builder2.onPositive((dialog2, which) -> {
                        mPdfOptions.setOutFileName(filename);
                        if (isGrayScale)
                            saveImagesInGrayScale();
                        new CreatePdf(mPdfOptions, mHomePath, ImageToPdfFragment.this).execute();
                    }).onNegative((dialog1, which) -> createPdf(isGrayScale)).show();
                }
            }
        }).show();
    }

    @OnClick(R.id.pdfOpen)
    void openPdf() {
        mFileUtils.openFile(mPath, FileUtils.FileType.e_PDF);
    }


    /**
     * Called after user is asked to grant permissions
     *
     * @param requestCode  REQUEST Code for opening permissions
     * @param permissions  permissions asked to user
     * @param grantResults bool array indicating if permission is granted
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (grantResults.length < 1)
            return;

        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mPermissionGranted = true;
                selectImages();
                StringUtils.getInstance().showSnackbar(mActivity, R.string.snackbar_permissions_given);
            } else
                StringUtils.getInstance().showSnackbar(mActivity, R.string.snackbar_insufficient_permissions);
        }
    }

    /**
     * Called after Matisse Activity is called
     *
     * @param requestCode REQUEST Code for opening Matisse Activity
     * @param resultCode  result code of the process
     * @param data        Data of the image selected
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mIsButtonAlreadyClicked = false;
        if (resultCode != Activity.RESULT_OK || data == null)
            return;

        switch (requestCode) {
            case INTENT_REQUEST_GET_IMAGES:
                mImagesUri.clear();
                mUnarrangedImagesUri.clear();
                mImagesUri.addAll(Matisse.obtainPathResult(data));
                mUnarrangedImagesUri.addAll(mImagesUri);
                if (mImagesUri.size() > 0) {
                    mNoOfImages.setText(String.format(mActivity.getResources()
                            .getString(R.string.images_selected), mImagesUri.size()));
                    mNoOfImages.setVisibility(View.VISIBLE);
                    StringUtils.getInstance().showSnackbar(mActivity, R.string.snackbar_images_added);
                    mCreatePdf.setEnabled(true);
                }
                mOpenPdf.setVisibility(View.GONE);
                break;

            case CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE:
                HashMap<Integer, Uri> croppedImageUris =
                        (HashMap) data.getSerializableExtra(CropImage.CROP_IMAGE_EXTRA_RESULT);

                for (int i = 0; i < mImagesUri.size(); i++) {
                    if (croppedImageUris.get(i) != null) {
                        mImagesUri.set(i, croppedImageUris.get(i).getPath());
                        StringUtils.getInstance().showSnackbar(mActivity, R.string.snackbar_imagecropped);
                    }
                }
                break;

            case INTENT_REQUEST_APPLY_FILTER:
                mImagesUri.clear();
                ArrayList<String> mFilterUris = data.getStringArrayListExtra(RESULT);
                int size = mFilterUris.size() - 1;
                for (int k = 0; k <= size; k++)
                    mImagesUri.add(mFilterUris.get(k));
                break;

            case INTENT_REQUEST_PREVIEW_IMAGE:
                mImagesUri = data.getStringArrayListExtra(RESULT);
                if (mImagesUri.size() > 0) {
                    mNoOfImages.setText(String.format(mActivity.getResources()
                            .getString(R.string.images_selected), mImagesUri.size()));
                } else {
                    mNoOfImages.setVisibility(View.GONE);
                    mCreatePdf.setEnabled(false);
                }
                break;

            case INTENT_REQUEST_REARRANGE_IMAGE:
                mImagesUri = data.getStringArrayListExtra(RESULT);
                if (!mUnarrangedImagesUri.equals(mImagesUri) && mImagesUri.size() > 0) {
                    mNoOfImages.setText(String.format(mActivity.getResources()
                            .getString(R.string.images_selected), mImagesUri.size()));
                    StringUtils.getInstance().showSnackbar(mActivity, R.string.images_rearranged);
                    mUnarrangedImagesUri.clear();
                    mUnarrangedImagesUri.addAll(mImagesUri);
                }
                if (mImagesUri.size() == 0) {
                    mNoOfImages.setVisibility(View.GONE);
                    mCreatePdf.setEnabled(false);
                }
                break;
        }
    }

    /**
     * Saves Images with gray scale filter
     */
    private void saveImagesInGrayScale() {
        ArrayList<String> tempImageUri = new ArrayList<>();
        try {
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/PDFfilter");
            dir.mkdirs();

            int size = mImagesUri.size();
            for (int i = 0; i < size; i++) {
                String fileName = String.format(getString(R.string.filter_file_name),
                        String.valueOf(System.currentTimeMillis()), i + "_grayscale");
                File outFile = new File(dir, fileName);
                String imagePath = outFile.getAbsolutePath();

                File f = new File(mImagesUri.get(i));
                FileInputStream fis = new FileInputStream(f);
                Bitmap bitmap = BitmapFactory.decodeStream(fis);
                Bitmap grayScaleBitmap = ImageUtils.getInstance().toGrayscale(bitmap);

                File file = new File(imagePath);
                file.createNewFile();
                FileOutputStream ostream = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(ostream, 1024 * 8);
                grayScaleBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                bos.flush();
                bos.close();
                ostream.close();
                tempImageUri.add(imagePath);
            }
            mImagesUri.clear();
            mImagesUri.addAll(tempImageUri);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPDFCreationStarted() {
        mMaterialDialog = DialogUtils.getInstance().createAnimationDialog(mActivity);
        mMaterialDialog.show();
    }

    @Override
    public void onPDFCreated(boolean success, String path) {
        if (mMaterialDialog != null && mMaterialDialog.isShowing())
            mMaterialDialog.dismiss();

        if (!success) {
            StringUtils.getInstance().showSnackbar(mActivity, R.string.snackbar_folder_not_created);
            return;
        }
        StringUtils.getInstance().getSnackbarwithAction(mActivity, R.string.snackbar_pdfCreated)
                .setAction(R.string.snackbar_viewAction,
                        v -> mFileUtils.openFile(mPath, FileUtils.FileType.e_PDF)).show();
        mOpenPdf.setVisibility(View.VISIBLE);
        mPath = path;
        resetValues();
    }


    private void getRuntimePermissions() {
        PermissionsUtils.getInstance().requestRuntimePermissions(this,
                READ_WRITE_CAMERA_PERMISSIONS,
                REQUEST_PERMISSIONS_CODE);
    }

    /**
     * Opens Matisse activity to select Images
     */
    private void selectImages() {
        Matisse.from(this)
                .choose(MimeType.ofImage(), false)
                .countable(true)
                .capture(true)
                .captureStrategy(new CaptureStrategy(true, Constants.AUTHORITY_APP))
                .maxSelectable(1000)
                .imageEngine(new PicassoEngine())
                .forResult(INTENT_REQUEST_GET_IMAGES);
    }

    /**
     * Resets pdf creation related values & show enhancement options
     */
    private void resetValues() {
        mPdfOptions = new ImageToPDFOptions();
        mPdfOptions.setBorderWidth(mSharedPreferences.getInt(Constants.DEFAULT_IMAGE_BORDER_TEXT,
                Constants.DEFAULT_BORDER_WIDTH));
        mPdfOptions.setQualityString(
                Integer.toString(mSharedPreferences.getInt(Constants.DEFAULT_COMPRESSION,
                        Constants.DEFAULT_QUALITY_VALUE)));
        mPdfOptions.setPageSize(mSharedPreferences.getString(Constants.DEFAULT_PAGE_SIZE_TEXT,
                Constants.DEFAULT_PAGE_SIZE));
        mPdfOptions.setPasswordProtected(false);
        mPdfOptions.setWatermarkAdded(false);
        mImagesUri.clear();
        mNoOfImages.setVisibility(View.GONE);
        ImageUtils.getInstance().mImageScaleType = mSharedPreferences.getString(Constants.DEFAULT_IMAGE_SCALE_TYPE_TEXT,
                IMAGE_SCALE_TYPE_ASPECT_RATIO);
        mPdfOptions.setMargins(0, 0, 0, 0);
        mPageNumStyle = mSharedPreferences.getString (Constants.PREF_PAGE_STYLE, null);
        mPageColor = mSharedPreferences.getInt(Constants.DEFAULT_PAGE_COLOR_ITP,
                DEFAULT_PAGE_COLOR);
    }

    private void addMargins() {
        mPdfOptions.setMargins(50, 50, 50, 50);
    }


    private void addPageNumbers() {

        SharedPreferences.Editor editor = mSharedPreferences.edit();
        mPageNumStyle = mSharedPreferences.getString (Constants.PREF_PAGE_STYLE, null);
        mChoseId = mSharedPreferences.getInt (Constants.PREF_PAGE_STYLE_ID, -1);
        mPageNumStyle=Constants.PG_NUM_STYLE_PAGE_X_OF_N;
    }

}
