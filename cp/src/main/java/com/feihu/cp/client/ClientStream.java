package com.feihu.cp.client;

import android.hardware.usb.UsbDevice;
import android.util.Pair;

import com.feihu.cp.R;
import com.feihu.cp.adb.Adb;
import com.feihu.cp.buffer.BufferStream;
import com.feihu.cp.client.decode.DecodecTools;
import com.feihu.cp.entity.AppData;
import com.feihu.cp.entity.Device;
import com.feihu.cp.entity.MyInterface;
import com.feihu.cp.helper.AppSettings;
import com.feihu.cp.helper.DeviceTools;
import com.feihu.cp.helper.PublicTools;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;


public class ClientStream {
    private boolean isClose = false;
    private boolean connectDirect = false;
    private final boolean connectByUsb;
    private Adb adb;
    private Socket mainSocket;
    private Socket videoSocket;
    private OutputStream mainOutputStream;
    private DataInputStream mainDataInputStream;
    private DataInputStream videoDataInputStream;
    private BufferStream mainBufferStream;
    private BufferStream videoBufferStream;
    private BufferStream shell;
    private Thread connectThread = null;
    private static final String serverName = "/data/local/tmp/easycontrol_server_" + DeviceTools.getVersionCode() + ".jar";
    private static final boolean supportH265 = DecodecTools.isSupportH265();
    private static final boolean supportOpus = DecodecTools.isSupportOpus();

    public ClientStream(Device device, UsbDevice usbDevice, MyInterface.MyFunctionBoolean handle) {
        connectByUsb = usbDevice != null;
        // 超时
        Thread timeOutThread = new Thread(() -> {
            try {
                Thread.sleep(10 * 1000);
                PublicTools.logToast("stream", AppData.applicationContext.getString(R.string.connect_time_out), true);
                AppData.uiHandler.post(() -> {
                    handle.run(false);
                });
                if (connectThread != null) connectThread.interrupt();
            } catch (InterruptedException ignored) {
                ignored.printStackTrace();
            }
        });
        // 连接
        connectThread = new Thread(() -> {
            try {
                Pair<String, Integer> address = null;
                if (!connectByUsb) address = PublicTools.getIpAndPort(device.address);
                adb = connectADB(address, usbDevice);
                startServer(device);
                connectServer(address);
                AppData.uiHandler.post(() -> {
                    handle.run(true);
                });
            } catch (Exception e) {
                PublicTools.logToast("stream", AppData.applicationContext.getString(R.string.connect_error), true);
                AppData.uiHandler.post(() -> {
                    handle.run(false);
                });
            } finally {
                timeOutThread.interrupt();
            }
        });
        connectThread.start();
        timeOutThread.start();
    }

    // 连接ADB
    private static Adb connectADB(Pair<String, Integer> address, UsbDevice usbDevice) throws Exception {
        if (address != null) return new Adb(address.first, address.second, AppData.keyPair);
        else return new Adb(usbDevice, AppData.keyPair);
    }

    // 启动Server
    private void startServer(Device device) throws Exception {
        if (/*BuildConfig.ENABLE_DEBUG_FEATURE || */!adb.runAdbCmd("ls /data/local/tmp/easycontrol_*").contains(serverName)) {
            adb.runAdbCmd("rm /data/local/tmp/easycontrol_* ");
            adb.pushFile(AppData.applicationContext.getResources().openRawResource(R.raw.easycontrol_server), serverName);
        }
        shell = adb.getShell();
        shell.write(ByteBuffer.wrap(("app_process -Djava.class.path=" + serverName + " / top.saymzx.easycontrol.server.Server"
                + " isAudio=1" + " maxSize=" + device.maxSize
                + " maxFps=" + AppSettings.getVideoFps()
                + " maxVideoBit=" + AppSettings.getVideoBits()
                + " keepAwake=" + (device.keepWakeOnRunning ? 1 : 0)
                + " supportH265=" + ((device.useH265 && supportH265) ? 1 : 0)
                + " supportOpus=" + (supportOpus ? 1 : 0) + " \n").getBytes()));
    }

    // 连接Server
    private void connectServer(Pair<String, Integer> address) throws Exception {
//    Thread.sleep(50);
        int reTry = 60;
//    long startTime = System.currentTimeMillis();
//    if (address != null) {
//      reTry /= 2;
//      for (int i = 0; i < reTry; i++) {
//        try {
//          if (mainSocket == null) {
//            mainSocket = new Socket();
//            mainSocket.connect(new InetSocketAddress(address.first, 25166), 1000);
//          }
//          if (videoSocket == null) {
//            videoSocket = new Socket();
//            videoSocket.connect(new InetSocketAddress(address.first, 25166), 1000);
//          }
//          mainOutputStream = mainSocket.getOutputStream();
//          mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
//          videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
//          connectDirect = true;
//          return;
//        } catch (Exception ignored) {
//          ignored.printStackTrace();
//          // 此处检查是因为代码是靠连接错误约束时间的，但有些设备为了安全，在端口没有开启的情况下不会回复reset错误，而是不回复，导致无法检测错误，无法约束时间
//          // 如果超时，直接跳出循环
//          if (System.currentTimeMillis() - startTime >= 5000) {
//            reTry = 60;
//            break;
//          }
//          Thread.sleep(50);
//        }
//      }
//    }
        // 直连失败尝试ADB中转
        for (int i = 0; i < reTry; i++) {
            try {
                if (mainBufferStream == null) mainBufferStream = adb.tcpForward(25166);
                // 为了减少adb同步阻塞的问题，此处分开音视频流
                if (videoBufferStream == null) videoBufferStream = adb.tcpForward(25166);
                return;
            } catch (Exception ignored) {
                ignored.printStackTrace();
                Thread.sleep(50);
            }
        }
        throw new Exception(AppData.applicationContext.getString(R.string.toast_connect_server));
    }

    public void runShell(String cmd) throws Exception {
        adb.runAdbCmd(cmd);
    }

    public byte readByteFromMain() throws IOException, InterruptedException {
        if (connectDirect) return mainDataInputStream.readByte();
        else return mainBufferStream.readByte();
    }

    public byte readByteFromVideo() throws IOException, InterruptedException {
        if (connectDirect) return videoDataInputStream.readByte();
        else return videoBufferStream.readByte();
    }

    public int readIntFromMain() throws IOException, InterruptedException {
        if (connectDirect) return mainDataInputStream.readInt();
        else return mainBufferStream.readInt();
    }

    public int readIntFromVideo() throws IOException, InterruptedException {
        if (connectDirect) return videoDataInputStream.readInt();
        else return videoBufferStream.readInt();
    }

    public ByteBuffer readByteArrayFromMain(int size) throws IOException, InterruptedException {
        if (connectDirect) {
            byte[] buffer = new byte[size];
            mainDataInputStream.readFully(buffer);
            return ByteBuffer.wrap(buffer);
        } else return mainBufferStream.readByteArray(size);
    }

    public ByteBuffer readByteArrayFromVideo(int size) throws IOException, InterruptedException {
        if (connectDirect) {
            byte[] buffer = new byte[size];
            videoDataInputStream.readFully(buffer);
            return ByteBuffer.wrap(buffer);
        }
        return videoBufferStream.readByteArray(size);
    }

    public ByteBuffer readFrameFromMain() throws Exception {
        if (!connectByUsb && !connectDirect) mainBufferStream.flush();
        return readByteArrayFromMain(readIntFromMain());
    }

    public ByteBuffer readFrameFromVideo() throws Exception {
        if (!connectByUsb && !connectDirect) videoBufferStream.flush();
        int size = readIntFromVideo();
        return readByteArrayFromVideo(size);
    }

    public void writeToMain(ByteBuffer byteBuffer) throws Exception {
        if (connectDirect) mainOutputStream.write(byteBuffer.array());
        else mainBufferStream.write(byteBuffer);
    }

    public void close() {
        if (isClose) return;
        isClose = true;
        if (shell != null)
            PublicTools.logToast("server", new String(shell.readByteArrayBeforeClose().array()), false);

        if (mainOutputStream != null) {
            try {
                mainOutputStream.close();
                mainOutputStream = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (videoDataInputStream != null) {
            try {
                videoDataInputStream.close();
                videoDataInputStream = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mainDataInputStream != null) {
            try {
                mainDataInputStream.close();
                mainDataInputStream = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mainSocket != null) {
            try {
                mainSocket.close();
                mainSocket = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (videoSocket != null) {
            try {
                videoSocket.close();
                videoSocket = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mainBufferStream != null) {
            mainBufferStream.close();
        }
        if (videoBufferStream != null) {
            videoBufferStream.close();
        }
        try {
            if (shell != null) {
                shell.close();
            }
            if (adb != null) {
                adb.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public static void runOnceCmd(Device device, UsbDevice usbDevice, String cmd, MyInterface.MyFunctionBoolean handle) {
        new Thread(() -> {
            try {
                Pair<String, Integer> address = null;
                if (usbDevice == null) address = PublicTools.getIpAndPort(device.address);
                Adb adb = connectADB(address, usbDevice);
                adb.runAdbCmd(cmd);
                adb.close();
                handle.run(true);
            } catch (Exception ignored) {
                ignored.printStackTrace();
                handle.run(false);
            }
        }).start();
    }

    public static void restartOnTcpip(Device device, UsbDevice usbDevice, MyInterface.MyFunctionBoolean handle) {
        new Thread(() -> {
            try {
                Pair<String, Integer> address = null;
                if (usbDevice == null) address = PublicTools.getIpAndPort(device.address);
                Adb adb = connectADB(address, usbDevice);
                String output = adb.restartOnTcpip(5555);
                adb.close();
                handle.run(output.contains("restarting"));
            } catch (Exception ignored) {
                ignored.printStackTrace();
                handle.run(false);
            }
        }).start();
    }

}
