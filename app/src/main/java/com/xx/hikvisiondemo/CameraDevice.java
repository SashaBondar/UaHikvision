package com.xx.hikvisiondemo;

/**
 * Класс объекта устройства
 */
public class CameraDevice {

    /**
     * IP-адрес
     */
    private String ip;
    /**
     * порт
     */
    private String port;
    /**
     * Имя пользователя
     */
    private String userName;
    /**
     * v
     */
    private String passWord;
    /**
     * Номер канала
     */
    private String channel;

    public String getIP() {
        return ip;
    }

    public void setIP(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public CameraDevice() {}
    public CameraDevice(String ip, String port, String userName, String passWord, String channel)
    {
        this.ip=ip;
        this.port=port;
        this.userName=userName;
        this.passWord=passWord;
        this.channel=channel;
    }

    @Override
    public String toString() {
        return "[IP=" + ip + "; PORT=" + port + "; USERNAME=" + userName + "; PASSWORD=" + passWord + "; CHANNEL=" + channel + ";]";
    }

}
