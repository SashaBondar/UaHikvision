package com.xx.hikvisiondemo;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;


/**
 * Хранить глобальные данные приложения приложений для достижения глобального обмена данными
 */
public class AppData extends Application {
    //Создать класс последовательной связи
//    public SeraliPortHelper portHelper = new SeraliPortHelper();

    //Получить начальные настройки данных
    public SharedPreferences preferences;
    public SharedPreferences.Editor editor;
    public static final String TOPIC_SUBCRIBE = "gk/light/3/1/d0001";//Подпишитесь на аппаратную часть
    public static final String TOPIC_PUBLIC = "gk/light/3/2/d0001";//Публикация аппаратной части

    public static final String TOPIC_BACK_SUBCRIBE = "gk/light/1/2/d0001";//Подписаться за кулисами
    public static final String TOPIC_BACK_PUBLIC = "gk/light/1/1/d0001";//Post background

    public static final String ADDRESS = "192.168.0.64";
    public static final int PORT = 8000;
    public static final String USER = "admin";
    public static final String PSD = "ghjnjy11";
    //Объект воспроизведения звука
    public static MediaPlayer mediaPlayer = new MediaPlayer();

    //Записать текущее состояние воспроизведения
    public boolean isPlaying = false;

    //Записать индекс воспроизводимой в данный момент трансляции
    private int playingIndex = 0;


    //Получить индекс текущей воспроизводимой трансляции
    public int getplayingIndex() {
        return this.playingIndex;
    }

    //Установите индекс текущей воспроизводимой трансляции
    public void setPlayingIndex(int index) {
        this.playingIndex = index;
    }

    //Записать статус ключевого вызова: true включен, false выключен
    public boolean isOpenCall = false;
    public boolean isOpenCamera = false;

    //Задайте параметры камеры
    public CameraDevice device = new CameraDevice("192.168.0.64", "8000", "admin", "ghjnjy11"/*"goockr86678686"*/, "1");

    @Override
    public void onCreate() {
        super.onCreate();
        //Получить начальные настройки данных
        preferences = getSharedPreferences("preferences", MODE_PRIVATE);
        editor = preferences.edit();
        setinitValue();
    }

    //Задайте начальное значение системы
    private void setinitValue() {
        int volumeValue = preferences.getInt("volume", 7);
        String cusZHName = preferences.getString("cusZHName", "Технология ореховой скорлупы");
        String cusENName = preferences.getString("cusENName", "Goockr");
        String passWord = preferences.getString("passWord", "123");
        //Запись данных
        String address = preferences.getString("callAddress", "Здание Jihua East Road Huiyuan");
        String latitude = preferences.getString("callLatitude", "N23.022");//широта
        String longitude = preferences.getString("callLongitude", "E133.176");//долгота

        //Звуковой сигнал с одним нажатием кнопки
        boolean isOpenAlarm = preferences.getBoolean("isOpenAlarm", true);

        //Отправить сохраненные данные
        editor.commit();
        if (volumeValue == 7) {
            editor.putInt("volume", 7);
            editor.commit();
        }
        if (cusZHName.equals("Технология ореховой скорлупы")) {
            editor.putString("cusZHName", "Технология ореховой скорлупы");
            editor.commit();
        }
        if (cusENName.equals("Goockr")) {
            editor.putString("cusENName", "Goockr");
            editor.commit();
        }
        if (passWord.equals("123")) {
            editor.putString("passWord", "123");
            editor.commit();
        }
        if (address.equals("Здание Jihua East Road Huiyuan")) {
            editor.putString("callAddress", "Здание Jihua East Road Huiyuan");
            editor.commit();
        }
        if (latitude.equals("N23.022")) {
            editor.putString("callLatitude", "N23.022");
            editor.commit();
        }
        if (longitude.equals("E133.176")) {
            editor.putString("callLongitude", "E133.176");
            editor.commit();
        }
        if (isOpenAlarm == true) {
            editor.putBoolean("isOpenAlarm", true);
            editor.commit();
        }

        //Установите начальное значение громкости 0-15
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeValue, AudioManager.FLAG_PLAY_SOUND);

    }
}
