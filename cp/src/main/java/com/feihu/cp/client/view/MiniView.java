//package com.feihu.cp.client.view;
//
//import android.annotation.SuppressLint;
//import android.graphics.PixelFormat;
//import android.os.Build;
//import android.view.Gravity;
//import android.view.LayoutInflater;
//import android.view.MotionEvent;
//import android.view.WindowManager;
//
//import java.nio.ByteBuffer;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import com.feihu.cp.client.ClientController;
//import com.feihu.cp.databinding.ModuleMiniViewBinding;
//import com.feihu.cp.entity.AppData;
//import com.feihu.cp.entity.Device;
//import com.feihu.cp.helper.PublicTools;
//import com.feihu.cp.helper.ViewTools;
//
//public class MiniView {
//
//  private final Device device;
//  private Thread timeoutListenerThread;
//  private long lastTouchTIme = 0;
//
//  // 迷你悬浮窗
//  private final ModuleMiniViewBinding miniView = ModuleMiniViewBinding.inflate(LayoutInflater.from(AppData.applicationContext));
//  private final WindowManager.LayoutParams miniViewParams = new WindowManager.LayoutParams(
//    WindowManager.LayoutParams.WRAP_CONTENT,
//    WindowManager.LayoutParams.WRAP_CONTENT,
//    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
//    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
//    PixelFormat.TRANSLUCENT
//  );
//
//  public MiniView(Device device) {
//    this.device = device;
//    miniViewParams.gravity = Gravity.START | Gravity.TOP;
//    miniViewParams.x = 0;
//    // 设置监听控制
//    setBarListener();
//    setButtonListener();
//  }
//
//  public void show(ByteBuffer byteBuffer) {
//    miniViewParams.y = device.miniY;
//    // 显示
//    ViewTools.viewAnim(miniView.getRoot(), true, PublicTools.dp2px(-40f), 0, (isStart -> {
//      if (isStart) AppData.windowManager.addView(miniView.getRoot(), miniViewParams);
//    }));
//    // 超时检测
//    if (device.miniTimeoutOnRunning && byteBuffer != null) {
//      lastTouchTIme = System.currentTimeMillis();
//      timeoutListenerThread = new Thread(() -> timeoutListener(new String(byteBuffer.array())));
//      timeoutListenerThread.start();
//    }
//  }
//
//  public void hide() {
//    try {
//      AppData.windowManager.removeView(miniView.getRoot());
//      if (timeoutListenerThread != null) timeoutListenerThread.interrupt();
//    } catch (Exception ignored) {
//    }
//  }
//
//  // 超时监听
//  private void timeoutListener(String timeoutAction) {
//    try {
//      long now;
//      while (!Thread.interrupted()) {
//        Thread.sleep(1);
//        now = System.currentTimeMillis();
//        if (now - lastTouchTIme > 5000) {
//          ClientController.handleControll(device.uuid, timeoutAction, null);
//          return;
//        }
//      }
//    } catch (Exception ignored) {
//    }
//  }
//
//  // 设置监听控制
//  @SuppressLint("ClickableViewAccessibility")
//  private void setBarListener() {
//    AtomicInteger yy = new AtomicInteger();
//    AtomicInteger oldYy = new AtomicInteger();
//    miniView.getRoot().setOnTouchListener((v, event) -> {
//      switch (event.getActionMasked()) {
//        case MotionEvent.ACTION_OUTSIDE:
//          lastTouchTIme = System.currentTimeMillis();
//          break;
//        case MotionEvent.ACTION_DOWN: {
//          yy.set((int) event.getRawY());
//          oldYy.set(miniViewParams.y);
//          break;
//        }
//        case MotionEvent.ACTION_MOVE: {
//          miniViewParams.y = oldYy.get() + (int) event.getRawY() - yy.get();
//          device.miniY = miniViewParams.y;
//          AppData.windowManager.updateViewLayout(miniView.getRoot(), miniViewParams);
//          break;
//        }
//      }
//      return true;
//    });
//  }
//
//  // 设置按钮监听
//  private void setButtonListener() {
//    miniView.buttonSmall.setOnClickListener(v -> ClientController.handleControll(device.uuid, "changeToSmall", null));
//    miniView.buttonFull.setOnClickListener(v -> ClientController.handleControll(device.uuid, "changeToFull", null));
//  }
//
//}
