
package com.xx.hikvisiondemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Class: CrashUtil
 * Description:
 */
public class CrashUtil implements Thread.UncaughtExceptionHandler
{
    private static final String TAG = "CrashUtil";

    private static final String SD_CARD_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SimpleDemo/crash";
    private Thread.UncaughtExceptionHandler mDefaultCrashHandler;

    /** Хранить информацию об устройстве и информацию об исключении */
    private final Map<String, String> infos = new HashMap<String, String>();

    /** Отформатируйте дату как часть имени файла журнала */
    private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault());

    /** Класс singleton*/
    private static CrashUtil mInstance = null;

    private Context mContext;

    public static CrashUtil getInstance()
    {
        if (null == mInstance)
        {
            synchronized (CrashUtil.class)
            {
                if (null == mInstance)
                {
                    mInstance = new CrashUtil();
                }
            }
        }
        return mInstance;
    }

    private CrashUtil()
    {

    }

    public void init(Context context)
    {
        mDefaultCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mContext = context;
    }

    @Override
    public void uncaughtException (Thread thread, Throwable ex)
    {
        handleException(ex);

        if (mDefaultCrashHandler != null)
        {
            // Пусть системный процессор по умолчанию
            SystemClock.sleep(500);
            mDefaultCrashHandler.uncaughtException(thread, ex);
        }
    }

    /** Пользовательская обработка ошибок, сбор сообщений об ошибках и многое другое */
    private boolean handleException (Throwable ex)
    {
        // Обработка исключений может побуждать пользователя к сбою, сохранению важной информации, попытке восстановления сайта или непосредственно к запуску процесса
        if (ex == null)
        {
            return false;
        }

        // Сбор информации об устройстве
        collectDeviceInfo();
        // Сохраните файл журнала
        saveCrashInfoToFile(ex);
        return true;
    }

    /** Сбор информации об устройстве */
    private void collectDeviceInfo ()
    {
        try
        {
            PackageManager pm = mContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null)
            {
                // Информация о версии приложения
                infos.put("App Version", pi.versionName + '_' + pi.versionCode + "\n");
                // Android Информация о версии системы
                infos.put("OS Version", Build.VERSION.RELEASE + '_' + Build.VERSION.SDK_INT + "\n");
                // ID устройства
                infos.put("Device ID", Build.ID + "\n");
                // Серийный номер устройства
                infos.put("Device Serial", Build.SERIAL + "\n");
                // Производитель мобильных телефонов
                infos.put("Manufacturer", Build.MANUFACTURER + "\n");
                // Модель телефона
                infos.put("Model", Build.MODEL + "\n");
                // Архитектура процессора
                infos.put("CPU ABI", Build.CPU_ABI + "\n");
                // Марка мобильного телефона
                infos.put("Brand", Build.BRAND + "\n");
            }
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.e(TAG, "an error occurred when collect package info");
            e.printStackTrace();
        }

//        Field[] fields = Build.class.getDeclaredFields();
//        for (Field field : fields)
//        {
//            try
//            {
//                field.setAccessible(true);
//                infos.put(field.getName(), field.get(null).toString());
//                LogUtil.d(field.getName() + " : " + field.get(null));
//            }
//            catch (IllegalAccessException e)
//            {
//                LogUtil.e("an error occured when collect crash info");
//                e.printStackTrace();
//            }
//        }
    }

    /** Сохраните сообщение об ошибке в файле */
    private String saveCrashInfoToFile (Throwable ex)
    {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : infos.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key);
            sb.append(" : ");
            sb.append(value);
            sb.append("\n");
        }

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null)
        {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();

        String result = writer.toString();
        sb.append("\n");
        sb.append(result);

        try
        {
            long currentTime = System.currentTimeMillis();
            String time = formatter.format(new Date(currentTime));
            String fileName = "crash_" + time + "_" + currentTime + ".log";

            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            {
                File dir = new File(SD_CARD_PATH);
                if (!dir.exists())
                {
                    boolean s = dir.mkdirs();
                    System.out.println(s);
                }

                FileOutputStream fileOutputStream = new FileOutputStream(SD_CARD_PATH + "/" + fileName);
                fileOutputStream.write(sb.toString().getBytes());
                fileOutputStream.close();
            }

            return fileName;
        }
        catch (Exception e)
        {
            Log.e(TAG, "an error occurred while writing file...");
            e.printStackTrace();
        }

        return "";
    }
}
