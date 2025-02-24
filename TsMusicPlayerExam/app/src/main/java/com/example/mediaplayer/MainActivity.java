package com.example.mediaplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    ListView listView;
    SimpleAdapter simpleAdapter;
    String[] songFile = {"Epigenesis.mp3", "Spinning Around.mp3", "Yorokobashiki-Ichinichi.mp3"};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = findViewById(R.id.listView_music);
        displayListView();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedSong = songFile[position];
                System.out.println("點選歌曲：" + selectedSong+" "+position);
                Intent intent=new Intent();
                intent.setClass(MainActivity.this,PlayingSongList.class);
                intent.putExtra("SongFile",songFile);
                intent.putExtra("position",position);
                startActivity(intent);
            }
        });

    }
    private void displayListView(){

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, songFile) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // 建立水平 LinearLayout
                LinearLayout itemLayout = new LinearLayout(MainActivity.this);;
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
                itemLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

                // 建立 ImageView
                ImageView albumArtView= new ImageView(MainActivity.this);
                int imageSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(imageSize, imageSize);
                albumArtView.setLayoutParams(imageParams);
                albumArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                //歌曲名稱
                TextView songTitleView = new TextView(MainActivity.this);
                songTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                int marginLeft = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
                textParams.setMargins(marginLeft, 0, 0, 0);
                songTitleView.setLayoutParams(textParams);

                // 將 ImageView 與 TextView 加入 LinearLayout
                itemLayout.addView(albumArtView);
                itemLayout.addView(songTitleView);

                String currentSong = getItem(position);
                songTitleView.setText(currentSong);

                // 取得 MP3 封面圖片，若無則使用預設圖示
                Bitmap art = null;
                try {
                    art = getAlbumArtFromAssets(currentSong);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (art != null) {
                    albumArtView.setImageBitmap(art);
                } else {
                    albumArtView.setImageResource(R.drawable.ic_launcher_foreground);
                }
                return itemLayout;
            }
        };

        listView.setAdapter(adapter);
    }
    private Bitmap getAlbumArtFromAssets(String fileName) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            AssetFileDescriptor afd = getAssets().openFd(fileName);
            retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            retriever.release();
        }
        return null;
    }
}