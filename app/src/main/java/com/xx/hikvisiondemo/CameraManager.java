package com.xx.hikvisiondemo;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;

import com.hikvision.netsdk.HCNetSDK;
import com.hikvision.netsdk.NET_DVR_DEVICEINFO_V30;
import com.hikvision.netsdk.NET_DVR_PREVIEWINFO;
import com.hikvision.netsdk.PTZCommand;
import com.hikvision.netsdk.PTZPresetCmd;
import com.hikvision.netsdk.RealPlayCallBack;

import org.MediaPlayer.PlayM4.Player;
import org.MediaPlayer.PlayM4.PlayerCallBack;

/**
 * Created by JJT-ssd on 2016/9/23.
 */
public class CameraManager {
    private final static String TAG = "HC_DEBUG";
    public final static String ACTION_START_RENDERING = "action_start_rendering";
    public final static String ACTION_DVR_OUTLINE = "action_dvr_outline";
    /**
     * Информация об устройстве Число аналоговых каналов bychannum Количество цифровых каналов byipchanum
     */
    private NET_DVR_DEVICEINFO_V30 deviceInfo_V30 = null;
    /**
     * c
     */
    private int m_iLogID = -1;

    /**
     * Маркер воспроизведения -1 не воспроизводится, 0 играет
     */
    private int m_iPlayID = -1;
    private int m_iPort = -1;
    private String ip;
    private int port;
    private String username;
    private String password;
    private int channel;
    private SurfaceHolder holder;
    /**
     * Контекст для трансляции
     */
    private Context context;

    private CameraManager manager = null;
    private boolean m_bStopPlayback;

    public CameraManager() {

    }

//    public static  synchronized CameraManager getInstance() {
//        if (manager == null) {
//            manager = new CameraManager();
//        }
//        return manager;
//    }

    /**
     * Установка информации об устройстве воспроизведения
     *
     * @param device
     */
    public void setCameraDevice(CameraDevice device) {
        this.ip = device.getIP();
        this.port = Integer.parseInt(device.getPort());
        this.username = device.getUserName();
        this.password = device.getPassWord();
        this.channel = Integer.parseInt(device.getChannel());
    }

    /**
     * Установите окно просмотра воспроизведения
     *
     * @param holder
     */
    public void setSurfaceHolder(SurfaceHolder holder) {
        this.holder = holder;
    }

    /**
     * Контекст, используемый для отправки трансляции
     *
     * @param context
     */
    public void setContext(Context context) {
        this.context = context;
    }

    public void initSDK() {
        if (m_iPlayID >= 0) {
            stopPlay();
        }
        if (HCNetSDK.getInstance().NET_DVR_Init()) {
            Log.i(TAG, "Успех инициализации SDK!");
        } else {
            Log.e(TAG, "Инициализировать SDK не удалось!");
        }
    }

    public void loginDevice() {
        deviceInfo_V30 = new NET_DVR_DEVICEINFO_V30();
        m_iLogID = HCNetSDK.getInstance().NET_DVR_Login_V30(ip, port, username,
                password, deviceInfo_V30);

        System.out.println("Вот информация об устройстве ************************");
        System.out.println("userId=" + m_iLogID);
        System.out.println("Начало канала =" + deviceInfo_V30.byStartChan);
        System.out.println("Количество каналов =" + deviceInfo_V30.byChanNum);
        System.out.println("Тип устройства =" + deviceInfo_V30.byDVRType);
        System.out.println("номер канала ip =" + deviceInfo_V30.byIPChanNum);

        if (m_iLogID < 0) {
            int errorCode = HCNetSDK.getInstance().NET_DVR_GetLastError();
            Log.e(TAG,
                    "Вход в устройство не удался!" + getErrorMsg(errorCode));
        } else {
            Log.i(TAG, "Успех устройства входа в систему!");
        }
    }


    //PreSetPosition для заданной точки, чтобы принять 1 для заданной точки с 1 по 2 для заданной точки 2
    public synchronized void realPlay(int preSetPosition) {
        try {
            if (m_iLogID < 0) {
                Log.e(TAG, "Попробуйте снова войти в систему");
                int count = 0;
                while (count < 10) {
                    Log.i(TAG, "Является первым" + (count + 1) + "Вход снова");
                    loginDevice();
                    if (m_iLogID < 0) {
                        count++;
                        SystemClock.sleep(300);
                    } else {
                        Log.i(TAG, "первый" + (count + 1) + "Вход успешно завершен");
                        break;
                    }
                }
                if (m_iLogID < 0) {
                    Log.e(TAG, "Попробуйте войти" + count + "Времена не удались!");
                    return;
                }
            }

            if (m_iPlayID < 0) {
                // Настройка параметров предварительного просмотра
//                NET_DVR_CLIENTINFO ClientInfo = new NET_DVR_CLIENTINFO();
                NET_DVR_PREVIEWINFO ClientInfo = new NET_DVR_PREVIEWINFO();
//                ClientInfo.lChannel = channel + deviceInfo_V30.byStartChan;
                ClientInfo.lChannel = 0;
                ClientInfo.dwStreamType = 0; // substream
                ClientInfo.bBlocked = 1;
                // Многоадресный адрес, конфигурация, требуемая при предварительном просмотре многоадресной рассылки
//                ClientInfo.sMultiCastIP = "";

                m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(
                        m_iLogID, ClientInfo, getRealPlayerCallBack());

                if (m_iPlayID < 0) {
                    int count = 0;
                    while (true) {
                        m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(
                                m_iLogID, ClientInfo, getRealPlayerCallBack());
                        if (m_iPlayID < 0) {
                            count++;
                            SystemClock.sleep(1000);
                        } else {
                            //Задайте точку по умолчанию
                            gotoPreSetPoint(preSetPosition);
                            break;
                        }
                    }
                    if (m_iPlayID < 0) {
                        Log.e(TAG, "Не удалось воспроизвести в реальном времени!"
                                + getErrorMsg(HCNetSDK.getInstance()
                                .NET_DVR_GetLastError()));
                        if (HCNetSDK.getInstance().NET_DVR_GetLastError() == 416) {
                            // Отправить радио
                            context.sendBroadcast(new Intent(ACTION_DVR_OUTLINE));
                        }
                        return;
                    }
                } else {
                    Log.i(TAG, "Начните играть в режиме реального времени!");
                    //Задайте точку по умолчанию
                    gotoPreSetPoint(preSetPosition);
                }
            } else {
                Log.d(TAG, "Теперь играешь?");
            }
        } catch (Exception e) {
            Log.e(TAG, "Исключение:" + e.toString());
        }
    }

    private void startSinglePreview() {
        if (m_iPlayID >= 0) {
            Log.i(TAG, "Please stop palyback first");
            return;
        }
        RealPlayCallBack fRealDataCallBack = getRealPlayerCbf();
        if (fRealDataCallBack == null) {
            Log.e(TAG, "fRealDataCallBack object is failed!");
            return;
        }
        Log.i(TAG, "m_iStartChan:" + 0);

        NET_DVR_PREVIEWINFO previewInfo = new NET_DVR_PREVIEWINFO();
        previewInfo.lChannel = 0;
        previewInfo.dwStreamType = 0; // substream
        previewInfo.bBlocked = 1;
//         NET_DVR_CLIENTINFO struClienInfo = new NET_DVR_CLIENTINFO();
//         struClienInfo.lChannel = m_iStartChan;
//         struClienInfo.lLinkMode = 0;
        // HCNetSDK start preview
        m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(m_iLogID,
                previewInfo, fRealDataCallBack);
//         m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V30(m_iLogID,
//         struClienInfo, fRealDataCallBack, false);
        if (m_iPlayID < 0) {
            Log.e(TAG, "NET_DVR_RealPlay is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return;
        }

        Log.i(TAG,
                "NetSdk Play sucess ***********************3***************************");
//        m_oPreviewBtn.setText("Stop");
    }

    /**
     * @return callback instance
     * @fn getRealPlayerCbf
     * @author zhuzhenlei
     * @brief get realplay callback instance
     */
    private RealPlayCallBack getRealPlayerCbf() {
        RealPlayCallBack cbf = new RealPlayCallBack() {
            public void fRealDataCallBack(int iRealHandle, int iDataType,
                                          byte[] pDataBuffer, int iDataSize) {
                // player channel 1
                processRealData(1, iDataType, pDataBuffer,
                        iDataSize, Player.STREAM_REALTIME);
            }
        };
        return cbf;
    }

    private boolean m_bNeedDecode = true;

    /**
     * @param iPlayViewNo - player channel [in]
     * @param iDataType   - data type [in]
     * @param pDataBuffer - data buffer [in]
     * @param iDataSize   - data size [in]
     * @param iStreamMode - stream mode [in]
     * @return NULL
     * @fn processRealData
     * @author zhuzhenlei
     * @brief process real data
     */
    public void processRealData(int iPlayViewNo, int iDataType,
                                byte[] pDataBuffer, int iDataSize, int iStreamMode) {
        if (!m_bNeedDecode) {
            // Log.i(TAG, "iPlayViewNo:" + iPlayViewNo + ",iDataType:" +
            // iDataType + ",iDataSize:" + iDataSize);
        } else {
            if (HCNetSDK.NET_DVR_SYSHEAD == iDataType) {
                if (m_iPort >= 0) {
                    return;
                }
                m_iPort = Player.getInstance().getPort();
                if (m_iPort == -1) {
                    Log.e(TAG, "getPort is failed with: "
                            + Player.getInstance().getLastError(m_iPort));
                    return;
                }
                Log.i(TAG, "getPort succ with: " + m_iPort);
                if (iDataSize > 0) {
                    if (!Player.getInstance().setStreamOpenMode(m_iPort,
                            iStreamMode)) // set stream mode
                    {
                        Log.e(TAG, "setStreamOpenMode failed");
                        return;
                    }
                    if (!Player.getInstance().openStream(m_iPort, pDataBuffer,
                            iDataSize, 2 * 1024 * 1024)) // open stream
                    {
                        Log.e(TAG, "openStream failed");
                        return;
                    }
                    if (!Player.getInstance().play(m_iPort,
                            holder)) {
                        Log.e(TAG, "play failed");
                        return;
                    }
                    if (!Player.getInstance().playSound(m_iPort)) {
                        Log.e(TAG, "playSound failed with error code:"
                                + Player.getInstance().getLastError(m_iPort));
                        return;
                    }
                }
            } else {
                if (!Player.getInstance().inputData(m_iPort, pDataBuffer,
                        iDataSize)) {
                    // Log.e(TAG, "inputData failed with: " +
                    // Player.getInstance().getLastError(m_iPort));
                    for (int i = 0; i < 4000 && m_iPlayID >= 0
                            && !m_bStopPlayback; i++) {
                        if (Player.getInstance().inputData(m_iPort,
                                pDataBuffer, iDataSize)) {
                            break;

                        }

                        if (i % 100 == 0) {
                            Log.e(TAG, "inputData failed with: "
                                    + Player.getInstance()
                                    .getLastError(m_iPort) + ", i:" + i);
                        }

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();

                        }
                    }
                }

            }
        }

    }

    /**
     * Получить обратные вызовы в реальном времени
     *
     * @return
     */
    private RealPlayCallBack getRealPlayerCallBack() {
        return new RealPlayCallBack() {
            /**
             * iRealHandle 当前的预览句柄 iDataType 数据类型 pDataBuffer 存放数据的缓冲区指针
             * iDataSize 缓冲区大小
             */
            @Override
            public void fRealDataCallBack(int iRealHandle, int iDataType,
                                          byte[] pDataBuffer, int iDataSize) {
                processRealData(iDataType, pDataBuffer, iDataSize,
                        Player.STREAM_REALTIME);
            }
        };
    }

    ///pointNum>=1
    public void gotoPreSetPoint(int pointNum) {
        if (m_iLogID < 0) return;
        boolean isArrive = false;
        while (!isArrive) {
            isArrive = HCNetSDK.getInstance().NET_DVR_PTZPreset_Other(m_iLogID, 1, PTZPresetCmd.GOTO_PRESET, pointNum);
        }

    }

    public void setLoginId(int m_iLogID) {
        this.m_iLogID = m_iLogID;
    }

    /**
     * Декодирование видеопотока
     *
     * @param iDataType   Тип данных
     * @param pDataBuffer Буферный указатель для хранения данных
     * @param iDataSize   Размер буфера
     * @param iStreamMode Режим воспроизведения
     */
    private void processRealData(int iDataType, byte[] pDataBuffer,
                                 int iDataSize, int iStreamMode) {
        Log.d(TAG, "processRealData: " + iDataType);
        Log.d(TAG, "processRealData: " + pDataBuffer);
        Log.d(TAG, "processRealData: " + iDataSize);
        Log.d(TAG, "processRealData: " + iStreamMode);
        int i = 0;
        try {
            switch (iDataType) {
                case HCNetSDK.NET_DVR_SYSHEAD:
                    Log.d(TAG, "Обработка данных заголовка");
                    if (m_iPort >= 0) {
                        break;
                    }
                    m_iPort = Player.getInstance().getPort();
                    if (m_iPort == -1) break;//Не удалось получить игровой порт!
                    if (iDataSize > 0) {
                        if (!Player.getInstance().setStreamOpenMode(m_iPort, iStreamMode))
                            break; //Не удалось установить поток видео!
                        // Функция обратного вызова сканирования
                        PlayerCallBack.PlayerDisplayCB displayCB = new PlayerCallBack.PlayerDisplayCB() {
                            //就这个函数出毛病了
                            @Override
                            public void onDisplay(int arg0, byte[] arg1, int arg2,
                                                  int arg3, int arg4, int arg5, int arg6, int arg7) {
                                if (null != context) {
                                    context.sendBroadcast(new Intent(
                                            ACTION_START_RENDERING));
                                } else {
                                    Log.e(TAG,
                                            "Contextпуст! НетsetContext(Context context)？");
                                }
                                Log.d(TAG, "Запустите отображение экрана");
                                if (!Player.getInstance()
                                        .setDisplayCB(m_iPort, null)) {
                                    //Не удалось удалить обратный вызов дисплея!
                                }
                            }
                        };
                        if (!Player.getInstance().setDisplayCB(m_iPort, displayCB)) {
                            //Не удалось установить обратный вызов дисплея!
                            break;
                        }
                        if (!Player.getInstance().setDisplayBuf(m_iPort, 10)) { // Частота кадров, не установленная по умолчанию 15
                            //Установите максимальное количество буфера воспроизведения буфера!
                            break;
                        }
                        if (!Player.getInstance().openStream(m_iPort, pDataBuffer,
                                iDataSize, 2 * 1024 * 1024)) {
                            //не удалось откпыть видеопоток
                            break;
                        }
                        if (!Player.getInstance().play(m_iPort, holder)) {
                            //
                            break;
                        }
                    } else {
                        Log.e(TAG, "Видеопоток нет данных!");
                    }
                    break;
                case HCNetSDK.NET_DVR_STREAMDATA:
                case HCNetSDK.NET_DVR_STD_AUDIODATA:
                case HCNetSDK.NET_DVR_STD_VIDEODATA:
                    // Log.i(TAG, "Обработка данных потока");
                    if (iDataSize > 0 && m_iPort != -1) {
                        for (i = 0; i < 400; i++) {
                            if (Player.getInstance().inputData(m_iPort,
                                    pDataBuffer, iDataSize)) {
                                //Успех входных данных!
                                break;
                            }
                            Thread.sleep(10);
                        }
                        if (i == 400) {
                            Log.e(TAG, "Входные данные не удались!");
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Декодирование видеопотока ненормально!" + e.toString());
        }
    }

    public void startMove(int orientation, int m_iLogID) {
        if (m_iLogID < 0) {
            return;
        }
        switch (orientation) {
            case 9:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_RIGHT, 0);
                break;
            case 8:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_UP, 0);
                break;
            case 7:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_LEFT, 0);
                break;
            case 6:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_RIGHT, 0);
                break;
            case 5:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_AUTO, 0);
                break;
            case 4:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_LEFT, 0);
                break;
            case 3:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_RIGHT, 0);
                break;
            case 2:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_DOWN, 0);
                break;
            case 1:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_LEFT, 0);
                break;
            default:
                break;
        }
    }

    /**
     * Остановите перемещение параметра NET_DVR_PTZControl_Other: (тег воспроизведения, канал, код команды, начальный тег 0 или тег остановки 1)
     *
     * @param orientation Цифровое направление Jiugongge
     */
    public void stopMove(int orientation, int m_iLogID) {
        if (m_iLogID < 0) {
            return;
        }
        switch (orientation) {
            case 9:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_RIGHT, 1);
                break;
            case 8:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_UP, 1);
                break;
            case 7:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_LEFT, 1);
                break;
            case 6:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_RIGHT, 1);
                break;
            case 5:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_AUTO, 1);
                break;
            case 4:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_LEFT, 1);
                break;
            case 3:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_RIGHT, 1);
                break;
            case 2:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_DOWN, 1);
                break;
            case 1:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_LEFT, 1);
                break;
            default:
                break;
        }
    }

    /**
     * Начать масштабирование NET_DVR_PTZControl_Другие параметры: (игровая марка, канал, код команды, начальная отметка 0 или знак остановки 1)
     *
     * @param x -1 Уменьшить 1 Увеличить
     */
    public void startZoom(int x, int m_iLogID) {
//        if (m_iPlayID < 0) {
//            return;
//        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_OUT, 0);
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_IN, 0);
        }
    }

    /**
     * Прекратить масштабирование NET_DVR_PTZControl_Other параметры: (play mark, channel, script, start mark 0 или stop mark 1)
     *
     * @param x -1 Уменьшить 1 Увеличить
     */
    public void stopZoom(int x, int m_iLogID) {
        if (m_iLogID < 0) {
            return;
        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_OUT, 1);
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_IN, 1);

        }
    }

    /**
     * Начать масштабирование NET_DVR_PTZControl_Other Параметры: (play mark, channel, command code, start mark 0 или stop mark 1)
     *
     * @param x -1 проксимальный 1 дистальный
     */
    public void startFocus(int x) {
        if (m_iPlayID < 0) {
            return;
        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_NEAR, 0);
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_FAR, 0);
        }
    }

    /**
     * Остановка масштабирования NET_DVR_PTZControl_Other Параметры: (play mark, channel, command code, start mark 0 или stop mark 1)
     *
     * @param x -1 проксимальный 1 дистальный
     */
    public void stopFocus(int x) {
        if (m_iPlayID < 0) {
            return;
        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_NEAR, 1);

        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_FAR, 1);
        }

    }


    /**
     * Остановить игру
     */
    public synchronized void stopPlay() {
        if (m_iPlayID < 0) {
            return;//Остановился
        }
        // Остановить передачу в сети
        if (HCNetSDK.getInstance().NET_DVR_StopRealPlay(m_iPlayID)) {
            Log.i(TAG, "Прекратите воспроизведение в режиме реального времени!");
        } else {
            Log.e(TAG,
                    "Прекратить воспроизведение в режиме реального времени не удалось!"
                            + getErrorMsg(HCNetSDK.getInstance()
                            .NET_DVR_GetLastError()));
            return;
        }
        // Остановить локальное воспроизведение
        if (Player.getInstance().stop(m_iPort)) {
            Log.i(TAG, "Прекратите местный успех!");
        } else {
            Log.e(TAG, "Прекратить локальное воспроизведение не удалось!");
            return;
        }
        // Отключите видеопоток
        if (Player.getInstance().closeStream(m_iPort)) {
            Log.i(TAG, "Выключите видеопоток успешно!");
        } else {
            Log.e(TAG, "Выключить поток видео не удалось!");
            return;
        }
        // Отпустите порт воспроизведения
        if (Player.getInstance().freePort(m_iPort)) {
            Log.i(TAG, "Удалите порт воспроизведения!");
        } else {
            Log.e(TAG, "Отключить порт воспроизведения не удалось!");
            return;
        }
        // Сброс порта воспроизведения
        m_iPort = -1;
        // Сброс метки воспроизведения
        m_iPlayID = -1;
        Log.i(TAG, "Прекратите играть успешно!");
    }

    /**
     * Выход из устройства
     */
    public void logoutDevice() {
        stopPlay();
        if (HCNetSDK.getInstance().NET_DVR_Logout_V30(m_iLogID)) {
            m_iLogID = -1;
            Log.i(TAG, "Успех оборудования для выхода из системы!");
        } else {
            m_iLogID = 0;
            Log.e(TAG,
                    "Выйти из устройства не удалось!"
                            + getErrorMsg(HCNetSDK.getInstance()
                            .NET_DVR_GetLastError()));
        }
    }

    /**
     * Релиз Hikvision SDK
     */
    public void freeSDK() {
        // Очистка кеша
        if (HCNetSDK.getInstance().NET_DVR_Cleanup()) {
            Log.i(TAG, "Ресурсы SDK были успешными!");
        } else {
            Log.e(TAG, "Не удалось освободить ресурсы SDK!");
        }
    }

    //Предустановленная операция
    public void setPoint(int point,int m_iPlayID) {
        switch (point) {
            case 8://Задайте заданную точку

                break;
            case 9://Очистить заданную точку
                break;
            case 39://Перейти к настройке

                break;
        }
    }

    /**
     *Получить сообщение об ошибке
     *
     * @param errorCode
     * @return
     */
    private String getErrorMsg(int errorCode) {
        switch (errorCode) {
            case 0:
                return "[" + errorCode + " NET_DVR_NOERROR] Нет ошибки.";
            case 1:
                return "[" + errorCode
                        + " NET_DVR_PASSWORD_ERROR] Имя пользователя и пароль неверны. Неверное имя пользователя или пароль, введенные во время регистрации.";
            case 2:
                return "["
                        + errorCode
                        + " NET_DVR_NOENOUGHPRI] Недостаточный авторитет. Зарегистрированный пользователь не имеет разрешения на выполнение текущей операции на устройстве и может быть сравнен с конфигурацией параметров удаленного пользователя.";
            case 3:
                return "[" + errorCode + " NET_DVR_NOINIT] SDK не инициализирован.";
            case 4:
                return "[" + errorCode
                        + " NET_DVR_CHANNEL_ERROR] Номер канала неверен. Устройство не имеет соответствующего номера канала.";
            case 5:
                return "[" + errorCode + " NET_DVR_OVER_MAXLINK] Общее количество подключений к устройству превышает максимальное.";
            case 6:
                return "[" + errorCode
                        + " NET_DVR_VERSIONNOMATCH] Версия не соответствует. Варианты SDK и устройства не совпадают.";
            case 7:
                return "["
                        + errorCode
                        + " NET_DVR_NETWORK_FAIL_CONNECT] Не удалось подключить устройство. Оборудование не подключено к сети или сети, вызванное таймаутом и так далее.";
            case 8:
                return "[" + errorCode + " NET_DVR_NETWORK_SEND_ERROR] Не удалось отправить устройство.";
            case 9:
                return "[" + errorCode + " NET_DVR_NETWORK_RECV_ERROR] Не удалось получить данные с устройства.";
            case 10:
                return "[" + errorCode
                        + " NET_DVR_NETWORK_RECV_TIMEOUT] Получать данные с тайм-аута устройства.";
            case 11:
                return "["
                        + errorCode
                        + " NET_DVR_NETWORK_ERRORDATA] Исправленные данные неверны. Данные, отправленные на устройство или полученные с устройства, неверны, например, ввод значения, не поддерживаемого устройством при настройке удаленных параметров.";
            case 12:
                return "[" + errorCode + " NET_DVR_ORDER_ERROR] Неправильный порядок звонков.";
            case 13:
                return "[" + errorCode + " NET_DVR_OPERNOPERMIT] Нет такой власти.";
            case 14:
                return "[" + errorCode + " NET_DVR_COMMANDTIMEOUT] Выполнение команды устройства завершено.";
            case 15:
                return "[" + errorCode
                        + " NET_DVR_ERRORSERIALPORT] Номер последовательного порта неверен. Указанный номер последовательного порта устройства не существует.";
            case 16:
                return "[" + errorCode
                        + " NET_DVR_ERRORALARMPORT] Тревожный порт неверен. Выходной порт аварийного сигнала устройства не существует.";
            case 17:
                return "["
                        + errorCode
                        + " NET_DVR_PARAMETER_ERROR] Ошибка параметров. Входные или выходные параметры, введенные в интерфейс SDK, пусты, или формат или значение параметра не соответствуют требованиям.";
            case 18:
                return "[" + errorCode + " NET_DVR_CHAN_EXCEPTION] Канал устройства находится в состоянии ошибки.";
            case 19:
                return "[" + errorCode
                        + " NET_DVR_NODISK] Устройство без жесткого диска. Когда устройство не имеет жесткого диска, операция не выполняется в видеофайле, конфигурации жесткого диска и т. Д. Устройства.";
            case 20:
                return "["
                        + errorCode
                        + " NET_DVR_ERRORDISKNUM] Ошибка номера жесткого диска. Эта ошибка возвращается, когда указанный номер диска не существует при выполнении операции управления диском на устройстве.";
            case 21:
                return "[" + errorCode + " NET_DVR_DISK_FULL] Полный жесткий диск устройства.";
            case 22:
                return "[" + errorCode + " NET_DVR_DISK_ERROR] Ошибка жесткого диска устройства.";
            case 23:
                return "[" + errorCode + " NET_DVR_NOSUPPORT] Устройство не поддерживается.";
            case 24:
                return "[" + errorCode + " NET_DVR_BUSY] Устройство занято。";
            case 25:
                return "[" + errorCode + " NET_DVR_MODIFY_FAIL] Модификация устройства не удалась.";
            case 26:
                return "[" + errorCode
                        + " NET_DVR_PASSWORD_FORMAT_ERROR] Неверный формат ввода пароля.";
            case 27:
                return "[" + errorCode + " NET_DVR_DISK_FORMATING] Жесткий диск отформатирован и не может быть запущен.";
            case 28:
                return "[" + errorCode + " NET_DVR_DVRNORESOURCE] Недостаточные ресурсы оборудования.";
            case 29:
                return "[" + errorCode + " NET_DVR_DVROPRATEFAILED] Не удалось выполнить операцию устройства.";
            case 30:
                return "["
                        + errorCode
                        + " NET_DVR_OPENHOSTSOUND_FAIL] Голосовая связь, голосовые трансляции для захвата локального звука или выхода из строя аудиосигнала.";
            case 31:
                return "[" + errorCode + " NET_DVR_DVRVOICEOPENED] Аппаратная голосовая связь занята.";
            case 32:
                return "[" + errorCode + " NET_DVR_TIMEINPUTERROR] Время не введено правильно.";
            case 33:
                return "[" + errorCode + " NET_DVR_NOSPECFILE] У устройства нет файла, указанного при воспроизведении.";
            case 34:
                return "["
                        + errorCode
                        + " NET_DVR_CREATEFILE_ERROR] Ошибка при создании файла. Не удалось создать локальную запись, сохранить изображение, получить файл конфигурации и создать файл при удаленном загрузке видео.";
            case 35:
                return "["
                        + errorCode
                        + " NET_DVR_FILEOPENFAIL] Ошибка открытия файла. При установке файла конфигурации, обновления устройства не удалось открыть файл при загрузке файла запроса.";
            case 36:
                return "[" + errorCode + " NET_DVR_OPERNOTFINISH] Последняя операция не была завершена.";
            case 37:
                return "[" + errorCode + " NET_DVR_GETPLAYTIMEFAIL] Неправильное время воспроизведения.";
            case 38:
                return "[" + errorCode + " NET_DVR_PLAYFAIL] Ошибка воспроизведения.";
            case 39:
                return "[" + errorCode + " NET_DVR_FILEFORMAT_ERROR] Неверный формат файла.";
            case 40:
                return "[" + errorCode + " NET_DVR_DIR_ERROR] Неправильный путь.";
            case 41:
                return "[" + errorCode
                        + " NET_DVR_ALLOC_RESOURCE_ERROR] Ошибка выделения ресурсов SDK.";
            case 42:
                return "["
                        + errorCode
                        + " NET_DVR_AUDIO_MODE_ERROR] Ошибка режима звуковой карты. В настоящее время открытый режим воспроизведения звука не соответствует реальному режиму настройки.";
            case 43:
                return "[" + errorCode
                        + " NET_DVR_NOENOUGH_BUF] Буфер слишком мал. Недостаточно приема буфера данных устройства или буфера изображения.";
            case 44:
                return "[" + errorCode + " NET_DVR_CREATESOCKET_ERROR] SOCKET создает ошибку.";
            case 45:
                return "[" + errorCode + " NET_DVR_SETSOCKET_ERROR] Установите ошибку SOCKET.";
            case 46:
                return "[" + errorCode
                        + " NET_DVR_MAX_NUM] Наибольшее число. Количество зарегистрированных подключенных соединений, количество подключений предварительного просмотра превышает максимальное значение, поддерживаемое SDK.";
            case 47:
                return "[" + errorCode
                        + " NET_DVR_USERNOTEXIST] Пользователь не существует. Зарегистрированный идентификатор пользователя уже отменен или недоступен.";
            case 48:
                return "[" + errorCode
                        + " NET_DVR_WRITEFLASHERROR] Запишите ошибку FLASH. FLASH не удалось обновить устройство.";
            case 49:
                return "[" + errorCode
                        + " NET_DVR_UPGRADEFAIL] Не удалось выполнить обновление устройства. Обновление завершилось неудачно из-за несоответствия языку сети или обновления.";
            case 50:
                return "[" + errorCode + " NET_DVR_CARDHAVEINIT] Плата декодирования была инициализирована.";
            case 51:
                return "[" + errorCode + " NET_DVR_PLAYERFAILED] Не удалось вызвать функцию в библиотеке.";
            case 52:
                return "[" + errorCode + " NET_DVR_MAX_USERNUM] Максимальное количество пользователей, подключенных к устройству.";
            case 53:
                return "[" + errorCode
                        + " NET_DVR_GETLOCALIPANDMACFAIL] Не удалось получить IP-адрес или физический адрес локального ПК.";
            case 54:
                return "[" + errorCode + " NET_DVR_NOENCODEING] У устройства нет начального кода для этого канала.";
            case 55:
                return "[" + errorCode + " NET_DVR_IPMISMATCH] IP-адрес не совпадает.";
            case 56:
                return "[" + errorCode + " NET_DVR_MACMISMATCH] MAC-адрес не совпадает.";
            case 57:
                return "[" + errorCode + " NET_DVR_UPGRADELANGMISMATCH] Язык файла обновления не соответствует.";
            case 58:
                return "[" + errorCode + " NET_DVR_MAX_PLAYERPORT] Максимальное количество игроков.";
            case 59:
                return "[" + errorCode + " NET_DVR_NOSPACEBACKUP] Недостаточно места в резервном устройстве для резервного копирования.";
            case 60:
                return "[" + errorCode + " NET_DVR_NODEVICEBACKUP] Не удалось найти указанное устройство резервного копирования.";
            case 61:
                return "[" + errorCode
                        + " NET_DVR_PICTURE_BITS_ERROR] Изображение простого номера не соответствует, ограничивает 24 цвета.";
            case 62:
                return "[" + errorCode
                        + " NET_DVR_PICTURE_DIMENSION_ERROR] Изображение высота * ширина ограничение, ограниченным до 128 * 256.";
            case 63:
                return "[" + errorCode
                        + " NET_DVR_PICTURE_SIZ_ERROR] Ограничение размера изображения, ограниченное 100 КБ.";
            case 64:
                return "[" + errorCode
                        + " NET_DVR_LOADPLAYERSDKFAILED] Ошибка игрока Sdk при загрузке текущего каталога.";
            case 65:
                return "[" + errorCode
                        + " NET_DVR_LOADPLAYERSDKPROC_ERROR] Не удается найти запись функции в Player Sdk.";
            case 66:
                return "[" + errorCode
                        + " NET_DVR_LOADDSSDKFAILED] DSsdk загружается в текущую ошибку каталога.";
            case 67:
                return "[" + errorCode
                        + " NET_DVR_LOADDSSDKPROC_ERROR] Не удается найти запись функции в DsSdk.";
            case 68:
                return "[" + errorCode
                        + " NET_DVR_DSSDK_ERROR] Не удалось вызвать функцию в DsSdk библиотеки жесткого декодирования.";
            case 69:
                return "[" + errorCode + " NET_DVR_VOICEMONOPOLIZE] Звуковая карта является эксклюзивной.";
            case 70:
                return "[" + errorCode + " NET_DVR_JOINMULTICASTFAILED] Не удалось присоединиться к группе многоадресной рассылки.";
            case 71:
                return "[" + errorCode + " NET_DVR_CREATEDIR_ERROR] Не удалось создать каталог файла журнала.";
            case 72:
                return "[" + errorCode + " NET_DVR_BINDSOCKET_ERROR] Не удалось соединить сокет.";
            case 73:
                return "["
                        + errorCode
                        + " NET_DVR_SOCKETCLOSE_ERROR] Соединение сокета прерывается, эта ошибка обычно возникает из-за недоступности соединения или назначения.";
            case 74:
                return "[" + errorCode + " NET_DVR_USERID_ISUSING] Идентификатор пользователя находится в процессе выхода из системы.";
            case 75:
                return "[" + errorCode + " NET_DVR_SOCKETLISTEN_ERROR] Не удалось выполнить мониторинг.";
            case 76:
                return "[" + errorCode + " NET_DVR_PROGRAM_EXCEPTION] Программа ненормальна.";
            case 77:
                return "["
                        + errorCode
                        + " NET_DVR_WRITEFILE_FAILED] Ошибка записи файла. Локальное видео, удаленное скачивание, загрузка изображений и других операций не удалось записать файл.";
            case 78:
                return "[" + errorCode + " NET_DVR_FORMAT_READONLY] Запретить форматирование жесткого диска только для чтения.";
            case 79:
                return "[" + errorCode
                        + " NET_DVR_WITHSAMEUSERNAME] Такое же имя пользователя существует в конфигурации конфигурации удаленного пользователя.";
            case 80:
                return "[" + errorCode + " NET_DVR_DEVICETYPE_ERROR] При импорте параметров несоответствие модели устройства.";
            case 81:
                return "[" + errorCode + " NET_DVR_LANGUAGE_ERROR] Языки не совпадают при импорте параметров.";
            case 82:
                return "[" + errorCode
                        + " NET_DVR_PARAVERSION_ERROR] Несоответствие версии программного обеспечения при импорте параметров.";
            case 83:
                return "[" + errorCode + " NET_DVR_IPCHAN_NOTALIVE] Внешний IP-канал не находится в режиме онлайн во время предварительного просмотра.";
            case 84:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SDK_ERROR] Ошибка загрузки стандартной библиотеки протоколов StreamTransClient.";
            case 85:
                return "[" + errorCode + " NET_DVR_CONVERT_SDK_ERROR] Не удалось загрузить инкапсуляцию включения.";
            case 86:
                return "[" + errorCode
                        + " NET_DVR_IPC_COUNT_OVERFLOW] Превышает максимальное количество IP-каналов доступа.";
            case 87:
                return "[" + errorCode
                        + " NET_DVR_MAX_ADD_NUM] Добавьте тег видео или другую операцию за пределы максимального количества поддержки.";
            case 88:
                return "["
                        + errorCode
                        + " NET_DVR_PARAMMODE_ERROR] Image Enhancer, ошибка режима параметра (для аппаратных настроек, настроек программного обеспечения клиента, неправильное значение).";
            case 89:
                return "[" + errorCode + " NET_DVR_CODESPITTER_OFFLINE] Сепаратор кода не подключен к сети.";
            case 90:
                return "[" + errorCode + " NET_DVR_BACKUP_COPYING] Устройство резервируется.";
            case 91:
                return "[" + errorCode + " NET_DVR_CHAN_NOTSUPPORT] Канал не поддерживает эту операцию.";
            case 92:
                return "[" + errorCode
                        + " NET_DVR_CALLINEINVALID] Строка высоты слишком концентрирована или длина линии недостаточно наклона.";
            case 93:
                return "[" + errorCode
                        + " NET_DVR_CALCANCELCONFLICT] Отмените конфликт калибровки, если вы установите правила и общий размер фильтра фактического размера.";
            case 94:
                return "[" + errorCode + " NET_DVR_CALPOINTOUTRANGE] Точка калибровки находится за пределами допустимого диапазона.";
            case 95:
                return "[" + errorCode + " NET_DVR_FILTERRECTINVALID] Размерный фильтр не соответствует требованиям.";
            case 96:
                return "[" + errorCode + " NET_DVR_DDNS_DEVOFFLINE] Устройство не зарегистрировано в ddns.";
            case 97:
                return "[" + errorCode + " NET_DVR_DDNS_INTER_ERROR] Внутренняя ошибка сервера DDNS.";
            case 99:
                return "[" + errorCode
                        + " NET_DVR_DEC_CHAN_REBIND] Ограничение отображения привязки канала декодирования ограничено.";
            case 150:
                return "[" + errorCode
                        + " NET_DVR_ALIAS_DUPLICATE] Alias duplicate (конфигурация HiDDNS).";
            case 200:
                return "[" + errorCode + " NET_DVR_NAME_NOT_ONLY] Имя уже существует.";
            case 201:
                return "[" + errorCode + " NET_DVR_OVER_MAX_ARRAY] Массив достигает верхнего предела.";
            case 202:
                return "[" + errorCode + " NET_DVR_OVER_MAX_VD] Виртуальный диск достиг своего предела.";
            case 203:
                return "[" + errorCode + " NET_DVR_VD_SLOT_EXCEED] Слот виртуального диска заполнен.";
            case 204:
                return "[" + errorCode
                        + " NET_DVR_PD_STATUS_INVALID] Для восстановления массива требуется неправильный статус физического диска.";
            case 205:
                return "[" + errorCode
                        + " NET_DVR_PD_BE_DEDICATE_SPARE] Необходимые физические диски для восстановления массива обозначаются как «Горячий резерв».";
            case 206:
                return "[" + errorCode + " NET_DVR_PD_NOT_FREE] Физические диски, необходимые для восстановления массива, не являются бесплатными.";
            case 207:
                return "[" + errorCode
                        + " NET_DVR_CANNOT_MIG2NEWMODE] Вы не можете перенести из текущего типа массива в новый тип массива.";
            case 208:
                return "[" + errorCode + " NET_DVR_MIG_PAUSE] Миграция приостановлена.";
            case 209:
                return "[" + errorCode + " NET_DVR_MIG_CANCEL] Выполненная миграция отменена.";
            case 210:
                return "[" + errorCode + " NET_DVR_EXIST_VD] В массиве есть виртуальный диск, который не может удалить массив.";
            case 211:
                return "[" + errorCode
                        + " NET_DVR_TARGET_IN_LD_FUNCTIONAL] Целевой физический диск является частью виртуального диска и работает правильно.";
            case 212:
                return "[" + errorCode
                        + " NET_DVR_HD_IS_ASSIGNED_ALREADY] Указанный физический диск назначается как виртуальный диск.";
            case 213:
                return "[" + errorCode
                        + " NET_DVR_INVALID_HD_COUNT] Количество физических дисков не соответствует указанному уровню RAID.";
            case 214:
                return "[" + errorCode + " NET_DVR_LD_IS_FUNCTIONAL] Массив является нормальным и не может быть перестроен.";
            case 215:
                return "[" + errorCode + " NET_DVR_BGA_RUNNING] Существует текущая фоновая задача.";
            case 216:
                return "[" + errorCode + " NET_DVR_LD_NO_ATAPI] Не удается создать виртуальный диск с диском ATAPI.";
            case 217:
                return "[" + errorCode + " NET_DVR_MIGRATION_NOT_NEED] Массив не нужно переносить.";
            case 218:
                return "[" + errorCode + " NET_DVR_HD_TYPE_MISMATCH] Физический диск не является типом согласия.";
            case 219:
                return "[" + errorCode + " NET_DVR_NO_LD_IN_DG] Нет виртуального диска, не может выполнить эту операцию.";
            case 220:
                return "[" + errorCode
                        + " NET_DVR_NO_ROOM_FOR_SPARE] Дисковое пространство слишком маленькое, чтобы его можно было назвать «горячим» резервом.";
            case 221:
                return "[" + errorCode
                        + " NET_DVR_SPARE_IS_IN_MULTI_DG] Диски были назначены в виде массива горячего резерва.";
            case 222:
                return "[" + errorCode + " NET_DVR_DG_HAS_MISSING_PD] Массив отсутствует.";
            case 223:
                return "[" + errorCode + " NET_DVR_NAME_EMPTY] Имя пуст.";
            case 224:
                return "[" + errorCode + " NET_DVR_INPUT_PARAM] Входные параметры неверны.";
            case 225:
                return "[" + errorCode + " NET_DVR_PD_NOT_AVAILABLE] Физический диск недоступен.";
            case 226:
                return "[" + errorCode + " NET_DVR_ARRAY_NOT_AVAILABLE] Массив недоступен.";
            case 227:
                return "[" + errorCode + " NET_DVR_PD_COUNT] Количество физических дисков неверно.";
            case 228:
                return "[" + errorCode + " NET_DVR_VD_SMALL] Виртуальный диск слишком мал.";
            case 229:
                return "[" + errorCode + " NET_DVR_NO_EXIST] Не существует.";
            case 230:
                return "[" + errorCode + " NET_DVR_NOT_SUPPORT] Эта операция не поддерживается.";
            case 231:
                return "[" + errorCode + " NET_DVR_NOT_FUNCTIONAL] Состояние массива не является нормальным.";
            case 232:
                return "[" + errorCode
                        + " NET_DVR_DEV_NODE_NOT_FOUND] Узел виртуального диска не существует.";
            case 233:
                return "[" + errorCode + " NET_DVR_SLOT_EXCEED] Слот достигает верхнего предела.";
            case 234:
                return "[" + errorCode + " NET_DVR_NO_VD_IN_ARRAY] В массиве нет виртуального диска.";
            case 235:
                return "[" + errorCode + " NET_DVR_VD_SLOT_INVALID] Недопустимый слот для виртуального диска.";
            case 236:
                return "[" + errorCode + " NET_DVR_PD_NO_ENOUGH_SPACE] Недопустимый слот для виртуального диска.";
            case 237:
                return "[" + errorCode
                        + " NET_DVR_ARRAY_NONFUNCTION] Только в нормальном состоянии массив может быть перенесен.";
            case 238:
                return "[" + errorCode + " NET_DVR_ARRAY_NO_ENOUGH_SPACE] Недостаточно пространства массива.";
            case 239:
                return "[" + errorCode
                        + " NET_DVR_STOPPING_SCANNING_ARRAY] Выполнение безопасного набора или повторного сканирования.";
            case 240:
                return "[" + errorCode + " NET_DVR_NOT_SUPPORT_16T] Создание массивов размером более 16T не поддерживается.";
            case 300:
                return "[" + errorCode + " NET_DVR_ID_ERROR] Идентификатор конфигурации не является разумным.";
            case 301:
                return "[" + errorCode + " NET_DVR_POLYGON_ERROR] Полигон не отвечает требованиям.";
            case 302:
                return "[" + errorCode + " NET_DVR_RULE_PARAM_ERROR] Параметры правила не являются разумными.";
            case 303:
                return "[" + errorCode + " NET_DVR_RULE_CFG_CONFLICT] Конфликт конфигурации.";
            case 304:
                return "[" + errorCode + " NET_DVR_CALIBRATE_NOT_READY] В настоящее время информация о калибровке отсутствует.";
            case 305:
                return "[" + errorCode + " NET_DVR_CAMERA_DATA_ERROR] Параметры камеры не являются разумными.";
            case 306:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DATA_UNFIT] Длина опрокидывания не способствует калибровке.";
            case 307:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DATA_CONFILICT] Ошибка калибровки, что все точки являются коллинеарными или слишком сфокусированными.";
            case 308:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_CALC_FAIL] Ошибка вычисления значения параметра калибровки камеры.";
            case 309:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_LINE_OUT_RECT] Введенная калибровочная линия образца находится за пределами ограничивающего рамки образца.";
            case 310:
                return "[" + errorCode + " NET_DVR_ENTER_RULE_NOT_READY] Не установлен в область.";
            case 311:
                return "["
                        + errorCode
                        + " NET_DVR_AID_RULE_NO_INCLUDE_LANE] Правила дорожного движения не включают дорожки (перегруженность и ретроградный трафик).";
            case 312:
                return "[" + errorCode + " NET_DVR_LANE_NOT_READY] В настоящее время нет подъездной дороги.";
            case 313:
                return "[" + errorCode
                        + " NET_DVR_RULE_INCLUDE_TWO_WAY] Правила инцидента включают два разных направления.";
            case 314:
                return "[" + errorCode
                        + " NET_DVR_LANE_TPS_RULE_CONFLICT] Переходы и правила данных конфликтуют.";
            case 315:
                return "[" + errorCode
                        + " NET_DVR_NOT_SUPPORT_EVENT_TYPE] Неподдерживаемый тип события.";
            case 316:
                return "[" + errorCode + " NET_DVR_LANE_NO_WAY] Лейн не имеет никакого направления.";
            case 317:
                return "[" + errorCode + " NET_DVR_SIZE_FILTER_ERROR] Размерный фильтр не является разумным.";
            case 318:
                return "[" + errorCode
                        + " NET_DVR_LIB_FFL_NO_FACE] Изображение, которое вы вводите при позиционировании точки объекта, не имеет лица.";
            case 319:
                return "[" + errorCode
                        + " NET_DVR_LIB_FFL_IMG_TOO_SMALL] Изображение, введенное при позиционировании функции, слишком мало.";
            case 320:
                return "[" + errorCode
                        + " NET_DVR_LIB_FD_IMG_NO_FACE] Изображения, введенные при обнаружении лица с одним изображением, не имеют лица.";
            case 321:
                return "[" + errorCode + " NET_DVR_LIB_FACE_TOO_SMALL] Моделирование лица слишком мало.";
            case 322:
                return "[" + errorCode
                        + " NET_DVR_LIB_FACE_QUALITY_TOO_BAD] При моделировании качество изображения на лице ухудшается.";
            case 323:
                return "[" + errorCode + " NET_DVR_KEY_PARAM_ERR] Ошибка расширенного параметра.";
            case 324:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DATA_ERR]Количество калибровочных образцов неверно, или значение данных неверно, или точка выборки находится за горизонтом.";
            case 325:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DISABLE_FAIL] Сконфигурированное правило не разрешает деа Калибровку.";
            case 800:
                return "[" + errorCode + " NET_DVR_DEV_NET_OVERFLOW] Сетевой трафик превышает лимит емкости устройства.";
            case 801:
                return "["
                        + errorCode
                        + " NET_DVR_STATUS_RECORDFILE_WRITING_NOT_LOCK] Видеофайлы в видео не могут быть заблокированы.";
            case 802:
                return "[" + errorCode
                        + " NET_DVR_STATUS_CANT_FORMAT_LITTLE_DISK] Из-за жесткого диска нельзя отформатировать.";
            case 901:
                return "[" + errorCode + " NET_ERR_WINCHAN_IDX] Неверный номер канала окна.";
            case 902:
                return "[" + errorCode
                        + " NET_ERR_WIN_LAYER] Неверное количество окон, максимальное количество окон, закрытых на одном экране. ";
            case 903:
                return "[" + errorCode
                        + " NET_ERR_WIN_BLK_NUM] Неверное количество окон, количество экранов, которые могут быть покрыты одним окном.";
            case 904:
                return "[" + errorCode + " NET_ERR_OUTPUT_RESOLUTION] Ошибка разрешения выходного сигнала.";
            case 905:
                return "[" + errorCode + " NET_ERR_LAYOUT] Ошибка компоновки.";
            case 906:
                return "[" + errorCode + " NET_ERR_INPUT_RESOLUTION] Разрешение ввода не поддерживается.";
            case 907:
                return "[" + errorCode + " NET_ERR_SUBDEVICE_OFFLINE] Под-устройство не подключено к сети.";
            case 908:
                return "[" + errorCode + " NET_ERR_NO_DECODE_CHAN] Нет свободного канала декодирования.";
            case 909:
                return "[" + errorCode + " NET_ERR_MAX_WINDOW_ABILITY] Высота потолка.";
            case 910:
                return "[" + errorCode + " NET_ERR_ORDER_ERROR] Вызывающая последовательность неверна. ";
            case 911:
                return "[" + errorCode + " NET_ERR_PLAYING_PLAN] План реализуется";
            case 912:
                return "[" + errorCode + " NET_ERR_DECODER_USED] Декодер используется.";
            case 401:
                return "[" + errorCode
                        + " NET_DVR_RTSP_ERROR_NOENOUGHPRI] Нет полномочий: сервер вернул 401, включится в этот код ошибки.";
            case 402:
                return "[" + errorCode
                        + " NET_DVR_RTSP_ERROR_ALLOC_RESOURCE] Не удалось выделить ресурсы.";
            case 403:
                return "[" + errorCode + " NET_DVR_RTSP_ERROR_PARAMETER] Ошибка параметров.";
            case 404:
                return "["
                        + errorCode
                        + " NET_DVR_RTSP_ERROR_NO_URL] Указанный URL-адрес не существует: сервер возвратил 404, включится в этот код ошибки.";
            case 406:
                return "[" + errorCode
                        + " NET_DVR_RTSP_ERROR_FORCE_STOP] Пользователь принудительного выхода.";
            case 407:
                return "[" + errorCode + " NET_DVR_RTSP_GETPORTFAILED] Получите ошибку порта RTSP.";
            case 410:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERROR] RTSP DECRIBE Интерактивная ошибка.";
            case 411:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBESENDTIMEOUT] RTSP DECRIBE Тайм-аут отправки.";
            case 412:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBESENDERROR] RTSP DECRIBE Не удалось отправить.";
            case 413:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERECVTIMEOUT] RTSP DECRIBE Тайм-аут приема.";
            case 414:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERECVDATALOST] RTSP DECRIBE Ошибка получения данных.";
            case 415:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERECVERROR] RTSP DECRIBE Сбой приема.";
            case 416:
                return "["
                        + errorCode
                        + " NET_DVR_RTSP_DESCRIBESERVERERR] RTSP DECRIBE Сервер вернул 401,501 ошибки.";
            case 420:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPERROR] RTSP SETUP Интерактивная ошибка.";
            case 421:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPSENDTIMEOUT] RTSP SETUP Тайм-аут отправки.";
            case 422:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPSENDERROR] RTSP SETUP Отправить ошибку.";
            case 423:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPRECVTIMEOUT] RTSP SETUP Тайм-аут приема.";
            case 424:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPRECVDATALOST] RTSP SETUP Ошибка получения данных.";
            case 425:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPRECVERROR] RTSP SETUP Сбой приема.";
            case 426:
                return "[" + errorCode + " NET_DVR_RTSP_OVER_MAX_CHAN] Устройство превышает максимальное количество подключений.";
            case 430:
                return "[" + errorCode + " NET_DVR_RTSP_PLAYERlsROR] RTSP PLAY Интерактивная ошибка.";
            case 431:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYSENDTIMEOUT] RTSP PLAY Тайм-аут отправки.";
            case 432:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYSENDERROR] RTSP PLAY Отправить ошибку.";
            case 433:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYRECVTIMEOUT] RTSP PLAY Тайм-аут приема.";
            case 434:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYRECVDATALOST] RTSP PLAY Ошибка получения данных.";
            case 435:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYRECVERROR] RTSP PLAY Сбой приема.";
            case 436:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYSERVERERR] RTSP PLAY Устройство возвратило состояние ошибки.";
            case 440:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNERROR] RTSP TEARDOWN Интерактивная ошибка.";
            case 441:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNSENDTIMEOUT] RTSP TEARDOWN Тайм-аут отправки.";
            case 442:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNSENDERROR] RTSP TEARDOWN Отправить ошибку.";
            case 443:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNRECVTIMEOUT] RTSP TEARDOWN Тайм-аут приема.";
            case 444:
                return "["
                        + errorCode
                        + " NET_DVR_RTSP_TEARDOWNRECVDATALOST] RTSP TEARDOWN Ошибка получения данных.";
            case 445:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNRECVERROR] RTSP TEARDOWN Сбой приема.";
            case 446:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNSERVERERR] RTSP TEARDOWN Устройство возвратило состояние ошибки.";
            case 500:
                return "[" + errorCode + " NET_PLAYM4_NOERROR] Нет ошибки.";
            case 501:
                return "[" + errorCode + " NET_PLAYM4_PARA_OVER] Недопустимые входные параметры.";
            case 502:
                return "[" + errorCode + " NET_PLAYM4_ORDER_ERROR] Вызов неправильный.";
            case 503:
                return "[" + errorCode + " NET_PLAYM4_TIMER_ERROR] Ошибка настройки мультимедийных часов.";
            case 504:
                return "[" + errorCode + " NET_PLAYM4_DEC_VIDEO_ERROR] Не удалось декодировать видео.";
            case 505:
                return "[" + errorCode + " NET_PLAYM4_DEC_AUDIO_ERROR] Ошибка декодирования аудио.";
            case 506:
                return "[" + errorCode + " NET_PLAYM4_ALLOC_MEMORY_ERROR] Не удалось выполнить распределение памяти.";
            case 507:
                return "[" + errorCode + " NET_PLAYM4_OPEN_FILE_ERROR] Не удалось выполнить операцию с файлом.";
            case 508:
                return "[" + errorCode + " NET_PLAYM4_CREATE_OBJ_ERROR] Создать событие потока и т. Д. Не удалось.";
            case 509:
                return "[" + errorCode
                        + " NET_PLAYM4_CREATE_DDRAW_ERROR] Не удалось создать directDraw.";
            case 510:
                return "[" + errorCode
                        + " NET_PLAYM4_CREATE_OFFSCREEN_ERROR] Не удалось создать бэкэнд-кеш.";
            case 511:
                return "[" + errorCode + " NET_PLAYM4_BUF_OVER] Буфер заполнен, поток ввода не выполнен.";
            case 512:
                return "[" + errorCode
                        + " NET_PLAYM4_CREATE_SOUND_ERROR] Не удалось создать аудиоустройство.";
            case 513:
                return "[" + errorCode + " NET_PLAYM4_SET_VOLUME_ERROR] Не удалось установить громкость.";
            case 514:
                return "[" + errorCode
                        + " NET_PLAYM4_SUPPORT_FILE_ONLY] Этот интерфейс можно использовать только при воспроизведении файлов.";
            case 515:
                return "[" + errorCode
                        + " NET_PLAYM4_SUPPORT_STREAM_ONLY] Этот интерфейс можно использовать только при потоковой передаче.";
            case 516:
                return "[" + errorCode
                        + " NET_PLAYM4_SYS_NOT_SUPPORT] Система не поддерживает, декодер может работать только в Pentium";
            case 517:
                return "[" + errorCode + " NET_PLAYM4_FILEHEADER_UNKNOWN] Нет заголовка файла.";
            case 518:
                return "[" + errorCode
                        + " NET_PLAYM4_VERSION_INCORRECT] Варианты декодера и кодировщика не соответствуют.";
            case 519:
                return "[" + errorCode
                        + " NET_PALYM4_INIT_DECODER_ERROR] Не удалось инициализировать декодер.";
            case 520:
                return "[" + errorCode
                        + " NET_PLAYM4_CHECK_FILE_ERROR] Файл слишком короткий или поток кода не распознается.";
            case 521:
                return "[" + errorCode
                        + " NET_PLAYM4_INIT_TIMER_ERROR] Не удалось инициализировать мультимедийные часы.";
            case 522:
                return "[" + errorCode + " NET_PLAYM4_BLT_ERROR] Ошибка копирования бит.";
            case 523:
                return "[" + errorCode + " NET_PLAYM4_UPDATE_ERROR] Не удалось выполнить наложение.";
            case 524:
                return "[" + errorCode
                        + " NET_PLAYM4_OPEN_FILE_ERROR_MULTI] Не удалось открыть файл смешанного потока.";
            case 525:
                return "[" + errorCode
                        + " NET_PLAYM4_OPEN_FILE_ERROR_VIDEO] Не удалось открыть файл видеопотока.";
            case 526:
                return "[" + errorCode
                        + " NET_PLAYM4_JPEG_COMPRESS_ERROR] Ошибка сжатия JPEG.";
            case 527:
                return "[" + errorCode
                        + " NET_PLAYM4_EXTRACT_NOT_SUPPORT] Эта версия файла не поддерживается.";
            case 528:
                return "[" + errorCode
                        + " NET_PLAYM4_EXTRACT_DATA_ERROR] Не удалось удалить данные файла.";
            case 678:
                return "["
                        + errorCode
                        + " NET_QOS_ERR_SCHEDPARAMS_BAD_MINIMUM_INTERVAL] Минимальная ошибка интервала по умолчанию.";
            case 679:
                return "[" + errorCode
                        + " NET_QOS_ERR_SCHEDPARAMS_BAD_FRACTION] Оценка по умолчанию неверна.";
            case 680:
                return "[" + errorCode
                        + " NET_QOS_ERR_SCHEDPARAMS_INVALID_BANDWIDTH] Значение полосы пропускания по умолчанию недопустимо.";
            case 687:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_TOO_BIG] Пакет слишком велик.";
            case 688:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_LENGTH] Длина пакета неверна.";
            case 689:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_VERSION] Версия пакета неверна.";
            case 690:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_UNKNOW] Неизвестный пакет. ";
            case 695:
                return "[" + errorCode + " NET_QOS_ERR_OUTOFMEM] Недостаточно памяти.";
            case 696:
                return "[" + errorCode
                        + " NET_QOS_ERR_LIB_NOT_INITIALIZED] Библиотека Lib не инициализируется.";
            case 697:
                return "[" + errorCode + " NET_QOS_ERR_SESSION_NOT_FOUND] Разговора не найдено.";
            case 698:
                return "[" + errorCode + " NET_QOS_ERR_INVALID_ARGUMENTS] Недопустимый параметр.";
            case 699:
                return "[" + errorCode + " NET_QOS_ERROR] Ошибка Qos.";
            case 700:
                return "[" + errorCode + " NET_QOS_OK] Нет ошибки.";
            default:
                return "[" + errorCode + " NET_???_??] Неизвестная ошибка. ";
        }
    }

}
