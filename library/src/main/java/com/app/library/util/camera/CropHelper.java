package com.app.library.util.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import com.app.library.Constant;
import com.app.library.util.LogUtil;

import java.io.File;


/**
 * Created with Android Studio.
 * User: ryan@xisue.com
 * Date: 10/1/14
 * Time: 11:08 AM
 * Desc: CropHelper
 * Revision:
 * - 10:00 2014/10/03 Basic utils.
 * - 11:30 2014/10/03 Add static methods for generating crop intents.
 * - 15:00 2014/10/03 Finish the logic of handling crop intents.
 * - 12:20 2014/10/04 Add "scaleUpIfNeeded" crop options for scaling up cropped images if the size is too small.
 * - 16:30 2015/05/22 Fixed the error that crop from gallery doest work on some Kitkat devices.
 * - 23:30 2015/08/20 Add support to pick or capture photo without crop.
 * - 23:00 2015/09/05 Add compress features.
 */
public class CropHelper {

    public static final String TAG = "CropHelper";

    /**
     * request code of Activities or Fragments
     * You will have to change the values of the request codes below if they conflict with your own.
     */
    public static final int REQUEST_CROP = 127;
    public static final int REQUEST_CAMERA = 128;
    public static final int REQUEST_PICK = 129;

//    public static final String CROP_CACHE_FOLDER = "MlsFolder";

    public static Uri generateUri() {
        File cacheFolder = new File(Constant.DIR_CACHE);
        if (!cacheFolder.exists()) {
            try {
                boolean result = cacheFolder.mkdir();
                LogUtil.d(TAG, "generateUri " + cacheFolder + " result: " + (result ? "succeeded" : "failed"));
            } catch (Exception e) {
                LogUtil.e("generateUri failed: " + cacheFolder, e);
            }
        }
        String name = String.format("image-%d.jpg", System.currentTimeMillis());
        return Uri
                .fromFile(cacheFolder)
                .buildUpon()
                .appendPath(name)
                .build();
    }

    public static boolean isPhotoReallyCropped(Uri uri) {
        File file = new File(uri.getPath());
        long length = file.length();
        return length > 0;
    }

    private static String originalPath;
    public static void handleResult(CropHandler handler, int requestCode, int resultCode, Intent data) {
        if (handler == null) return;
        if (resultCode == Activity.RESULT_CANCELED) {
            handler.onCancel();
        } else if (resultCode == Activity.RESULT_OK) {
            CropParams cropParams = handler.getCropParams();
            if (cropParams == null) {
                handler.onFailed("CropHandler's params MUST NOT be null!");
                return;
            }
            switch (requestCode) {
                case REQUEST_PICK:
                case REQUEST_CROP:
                    if (isPhotoReallyCropped(cropParams.uri)) {
                        LogUtil.e( "REQUEST_CROP isPhotoReallyCropped");
                        onPhotoCropped(handler, cropParams);
                        break;
                    } else {
                        LogUtil.e("NO REQUEST_CROP isPhotoReallyCropped");
                        Context context = handler.getCropParams().context;
                        if (context != null) {
                            if (data != null && data.getData() != null) {
                                String path = CropFileUtils.getSmartFilePath(context, data.getData());
                                originalPath = path;
                                LogUtil.e("path" + path);
                                boolean result = CropFileUtils.copyFile(path, cropParams.uri.getPath());
                                if (!result) {
                                    handler.onFailed("Copy file to cached folder failed");
                                    break;
                                }
                            } else {
                                handler.onFailed("Returned data is null " + data);
                                break;
                            }
                        } else {
                            handler.onFailed("CropHandler's context MUST NOT be null!");
                        }
                    }
                case REQUEST_CAMERA:
                    if (cropParams.enable) {
                        if (cropParams.customer) {
                            // 1.调用系统裁剪
                            Intent intent = buildCropFromUriIntent(cropParams);
                            handler.handleIntent(intent, REQUEST_CROP);
                        } else {
                            //2.自定义裁剪
                            Uri originUri = cropParams.uri;
                            handler.onCompressed(originUri,originUri.toString());
                        }
                    } else {
                        if (cropParams.clip) {
                            if (cropParams.customer) {
                                // 1.调用系统裁剪
                                Intent intent = buildCropFromUriIntent(cropParams);
                                handler.handleIntent(intent, REQUEST_CROP);
                            } else {
                                //2.自定义裁剪
                                Uri originUri = cropParams.uri;
                                handler.onCompressed(originUri, originUri.toString());
                            }
                        } else {
                            LogUtil.d("Photo cropped!");
                            onPhotoCropped(handler, cropParams);
                        }
                    }
                    break;
            }
        }
    }

    private static void onPhotoCropped(CropHandler handler, CropParams cropParams) {
        if (cropParams.compress) {
            Uri originUri = cropParams.uri;
            LogUtil.e("onPhotoCropped compress"+originalPath+"originUri"+originUri);
            Uri compressUri = CropHelper.generateUri();
            CompressImageUtils.compressImageFile(cropParams, originUri, compressUri);
            CompressImageUtils.compressOriginImage(originUri);
            handler.onCompressed(compressUri,originUri.toString());
        } else {
            LogUtil.e( "onPhotoCropped compress No");
            handler.onPhotoCropped(cropParams.uri);
        }
    }
    // None-Crop Intents

    public static Intent buildGalleryIntent(CropParams params) {
        Intent intent;
        if (params.enable) {
            intent = buildCropIntent(Intent.ACTION_GET_CONTENT, params);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT)
                    .setType("image/*")
                    .putExtra(MediaStore.Images.Media.ORIENTATION, 0)
                    .putExtra(MediaStore.EXTRA_OUTPUT, params.uri);
        }
        return intent;
    }

    public static Intent buildCameraIntent(CropParams params) {
        return new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.Images.Media.ORIENTATION, 0)
        .putExtra(MediaStore.EXTRA_OUTPUT, params.uri);
    }

    // Crop Intents
    private static Intent buildCropFromUriIntent(CropParams params) {
        return buildCropIntent("com.android.camera.action.CROP", params);
    }

    private static Intent buildCropIntent(String action, CropParams params) {
        return new Intent(action)
                .setDataAndType(params.uri, params.type)
                .putExtra("crop", "true")
                .putExtra("scale", params.scale)
                .putExtra("aspectX", params.aspectX)
                .putExtra("aspectY", params.aspectY)
                .putExtra("outputX", params.outputX)
                .putExtra("outputY", params.outputY)
                .putExtra("return-data", params.returnData)
                .putExtra("outputFormat", params.outputFormat)
                .putExtra("noFaceDetection", params.noFaceDetection)
                .putExtra("scaleUpIfNeeded", params.scaleUpIfNeeded)
                .putExtra(MediaStore.EXTRA_OUTPUT, params.uri);
    }

    // Clear Cache

    public static boolean clearCacheDir() {
        File cacheFolder = new File(Constant.DIR_CACHE);
        if (cacheFolder.exists()) {
            for (File file : cacheFolder.listFiles()) {
                boolean result = file.delete();
                LogUtil.d("Delete " + file.getAbsolutePath() + (result ? " succeeded" : " failed"));
            }
            return true;
        }
        return false;
    }

    public static boolean clearCachedCropFile(Uri uri) {
        if (uri == null) return false;

        File file = new File(uri.getPath());
        if (file.exists()) {
            boolean result = file.delete();
            LogUtil.d("Delete " + file.getAbsolutePath() + (result ? " succeeded" : " failed"));
            return result;
        }
        return false;
    }
}
