package com.lhs.project;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.lhs.project.api.DetectionApi;
import com.lhs.project.api.NetworkClient;
import com.lhs.project.api.UserApi;
import com.lhs.project.model.UserRes;
import com.lhs.project.utils.Utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MainActivity extends AppCompatActivity {

    public static final int REQ_REGISTER_CHECK = 101;
    public static final int REQ_LOGIN_CHECK = 102;
    public static final int REQ_PHOTO_CAPTURE = 103;
    public static final int REQ_PHOTO_SELECTION = 104;

    String access_token;
    ImageButton camerabtn, albumbtn, gallerybtn;
    Button loginBtn;
    ProgressDialog progressDialog;

    private File photoFile;

    private final long FINISH_INTERVAL_TIME = 2000;
    private long backPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ?????????????????? ??????
        camerabtn = findViewById(R.id.camerabtn);
        camerabtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                camera();
            }
        });
        // ?????????????????? ??????
        albumbtn = findViewById(R.id.layout_album);
        albumbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                album();
            }
        });
        // ?????????????????? ??????
        gallerybtn = findViewById(R.id.gallerybtn);
        gallerybtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PhotoboardActivity.class);
                startActivityForResult(intent, REQ_LOGIN_CHECK);
            }
        });
        // login ??????
        loginBtn = findViewById(R.id.LoginBtn);
        LoginCheck();

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (loginBtn.getText().toString().equals("login")) {
                    // ????????? text??? login?????? ????????? ???????????? ?????????
                    Intent intent = new Intent(MainActivity.this,LoginActivity.class);
                    startActivityForResult(intent, REQ_LOGIN_CHECK);
                } else if (loginBtn.getText().toString().equals("logout")) {
                    // ????????? text??? logout?????? ???????????? ??????
                    showProgress("???????????? ????????????...");
                    Retrofit retrofit = NetworkClient.getRetrofitClient(MainActivity.this);
                    UserApi api = retrofit.create(UserApi.class);
                    Call<UserRes> call = api.userLogout("Bearer" + access_token);
                    call.enqueue(new Callback<UserRes>() {
                        @Override
                        public void onResponse(Call<UserRes> call, Response<UserRes> response) {
                            dismissProgress();
                            loginBtn.setText("login");
                            access_token = null;
                            // ????????? ?????????
                            SharedPreferences sp = getSharedPreferences(Utils.PREFERENCE_NAME,MODE_PRIVATE);
                            SharedPreferences.Editor editor = sp.edit();
                            editor.clear();
                            editor.commit();
                        }
                        @Override
                        public void onFailure(Call<UserRes> call, Throwable t) {

                        }
                    });
                }
            }
        });

    }

    private void camera(){
        int permissionCheck = ContextCompat.checkSelfPermission(
                MainActivity.this, Manifest.permission.CAMERA);

        if(permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.CAMERA} ,
                    1000);
            Toast.makeText(MainActivity.this, "????????? ?????? ???????????????.",
                    Toast.LENGTH_SHORT).show();
            return;
        } else {
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if(i.resolveActivity(MainActivity.this.getPackageManager())  != null  ){
                // ????????? ???????????? ?????????
                String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                photoFile = getPhotoFile(fileName);

                Uri fileProvider = FileProvider.getUriForFile(MainActivity.this,
                        BuildConfig.APPLICATION_ID, photoFile);
                i.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider);
                startActivityForResult(i, REQ_PHOTO_CAPTURE);

            } else{
                Toast.makeText(MainActivity.this, "???????????? ????????? ?????? ????????????.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void album(){

        if(checkPermission()){
            displayFileChoose();
        }else{
            requestPermission();
        }
    }

    private File getPhotoFile(String fileName) {
        File storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        try{
            return File.createTempFile(fileName, ".jpg", storageDirectory);
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    private boolean checkPermission(){
        int result = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if(result == PackageManager.PERMISSION_DENIED){
            return false;
        }else{
            return true;
        }
    }

    private void displayFileChoose() {
        Intent i = new Intent();
        i.setType("image/*");
//        i.setAction(Intent.ACTION_GET_CONTENT);
        // ???????????? ????????????
        i.setAction(Intent.ACTION_PICK);
        startActivityForResult(Intent.createChooser(i, "SELECT IMAGE"), REQ_PHOTO_SELECTION);
    }
    private void requestPermission() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)){
            Toast.makeText(MainActivity.this, "?????? ????????? ???????????????.",
                    Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 500);
        }
        else{
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 500);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case 1000: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "?????? ?????? ?????????",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "?????? ???????????? ?????????",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case 500: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "?????? ?????? ?????????",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "?????? ???????????? ?????????",
                            Toast.LENGTH_SHORT).show();
                }

            }

        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        // ????????? ????????? ???????????? ????????? ?????? text login/logout??? ??????.
        // ?????? ??????????????? ???????????? ??????
        LoginCheck();

        if(requestCode == REQ_PHOTO_CAPTURE && resultCode == RESULT_OK){

            Bitmap photo = BitmapFactory.decodeFile(photoFile.getAbsolutePath());

            ExifInterface exif = null;
            try {
                exif = new ExifInterface(photoFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            photo = rotateBitmap(photo, orientation);

            // ???????????????. ????????? ?????????
            OutputStream os;
            try {
                os = new FileOutputStream(photoFile);
                photo.compress(Bitmap.CompressFormat.JPEG, 50, os);
                os.flush();
                os.close();
            } catch (Exception e) {
                Log.e(getClass().getSimpleName(), "Error writing bitmap", e);
            }

            Intent i = new Intent(MainActivity.this, ResultphotoActivity.class);
            i.putExtra("type", "camera");
            i.putExtra("filePath",  photoFile.getAbsolutePath());
            startActivity(i);

        }else if(requestCode == REQ_PHOTO_SELECTION && resultCode == RESULT_OK && data != null &&
                data.getData() != null){

            Uri albumUri = data.getData( );

            Intent i = new Intent(MainActivity.this, ResultphotoActivity.class);
            i.putExtra("type", "album");
            i.putExtra("imageUri", albumUri.toString());
            startActivity(i);

        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    // ????????? ?????? ?????? text
    public void LoginCheck() {
        // ?????? ??? ????????????
        SharedPreferences sp = getSharedPreferences(Utils.PREFERENCE_NAME, MODE_PRIVATE);
        access_token = sp.getString("access_Token", null);
        // ?????? text??? ????????? ????????? logout, ????????? login?????? ??????
        if(access_token!=null) {
            loginBtn.setText("logout");
        } else {
            loginBtn.setText("login");
        }
    }

    private void showProgress(String message) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        }
        catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    private void dismissProgress() {
        progressDialog.dismiss();
    }

    @Override
    public void onBackPressed() {
        long tempTime = System.currentTimeMillis();
        long intervalTime = tempTime - backPressedTime;

        if (0 <= intervalTime && FINISH_INTERVAL_TIME >= intervalTime)
        {
            finish();
        }
        else
        {
            backPressedTime = tempTime;
            Toast.makeText(getApplicationContext(), "??????????????? ?????? ??? ???????????????.", Toast.LENGTH_SHORT).show();
        }
    }
}