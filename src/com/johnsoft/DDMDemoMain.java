/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package com.johnsoft;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.android.ddmlib.AllocationInfo;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.HandleViewDebug;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ThreadInfo;

/**
 * @author John Kenrinus Lee
 * @version 2018-08-08
 */
public final class DDMDemoMain {
    public static final class MainThread extends Thread {
        private final LinkedBlockingDeque<Message> queue = new LinkedBlockingDeque<>();
        private final Map<Integer, Callback> map = Collections.synchronizedMap(new HashMap<>());

        public void register(int what, Callback callback) {
            if (what <= 0) {
                return;
            }
            map.put(what, callback);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final Message message = queue.takeFirst();
                    if (message != null) {
                        handleMessage(message);
                        if (message.what == -1) {
                            break;
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            System.exit(0);
        }

        public void init() {
            sendMessage(0, null);
        }

        public void quit() {
            sendMessage(-1, null);
        }

        public void sendMessage(int what, Object data) {
            queue.offerLast(new Message(what, data));
        }

        private void handleMessage(Message message) {
            final Callback callback = map.get(message.what);
            if (callback != null) {
                callback.handleMessage(message.what, message.data);
                return;
            }
            switch(message.what) {
                case 0: {
                    AndroidDebugBridge.init(true);
                }
                    break;
                case -1: {
                    AndroidDebugBridge.terminate();
                }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown message.what");
            }
        }

        private static final class Message {
            final int what;
            final Object data;

            Message(int what, Object data) {
                this.what = what;
                this.data = data;
            }
        }

        public interface Callback {
            void handleMessage(int what, Object data);
        }
    }

    public static final MainThread mainThread = new MainThread();

    public static final int CONNECT_DEVICES = 1;
    public static final int DISCONNECT_DEVICES = 2;
    public static final int LIST_PROPERTIES = 3;
    public static final int SELECT_CLIENT = 4;
    public static final int SELECT_VIEW_ROOT = 5;
    public static final int SELECT_VIEW_NODE = 6;
    public static final int CAPTURE_VIEW = 7;
    public static final int REFRESH_VIEW = 8;
    public static final int PROFILE_VIEW = 9;
    public static final int INVOKE_VIEW_METHOD = 10;
    public static final int NATIVE_HEAP_TRACK = 11;
    public static final int DUMP_THREAD = 12;

    public static final JFrame frame = new JFrame("AdbClient");
    public static final JPanel panel = new JPanel();

    private static IDevice device;
    private static Client client;
    private static String viewRoot;
    private static ViewNode viewNode;

    /*
     * ddmlib is core bridge between pc adb and phone adbd via jdwp debugger.
     * If not in android studio, dependencies ANDROID_HOME/tools/lib: ddmlib.jar and common.jar.
     * If in android studio, will use $ANDROID_STUDIO_HOME/plugins/android/lib/sdk-tools.jar.
     * Related jar:
     * $ANDROID_HOME/tools/lib: uast-26.0.0-dev.jar, asm-5.1.jar, lombok-ast-0.2.3.jar, lint-api-26.0.0-dev.jar
     * $ANDROID_STUDIO_HOME/plugins/android/lib: android.jar, studio-profiler-grpc-1.0-jarjar.jar
     * $ANDROID_STUDIO_HOME/lib: idea.jar
     *
     * Except checking ddmlib packages, also checking for help:
     * com/android/tools/idea/monitor/gpu/gfxinfohandlers
     * com/android/tools/idea/run
     * com.android.tools.profiler.proto
     *
     * Note profile way:
     * 1. ddmlib;
     * 2. adb shell dumpsys;
     * 3. adb shell cat /proc/xxx;
     * 4. ftrace/atrace;
     * 5. android/os/Debug.java;
     * 6. Android Studio 3.0 new profiler（ 1. use grpc, see https://grpc.io;
     *                                     2. as has server, client as apk part push to phone's /data/local/tmp/perfd,
     *                                        after app run, you will see "Studio:VmStats" thread;
     *                                     3. server's profile request send to client, client call android.os.Debug or
     *                                        dumpsys or c/c++ way to collect profile data, send as response back to
     *                                        server with protobuf, then as parse and show）
     */

    /*
     * /art/runtime/debugger.cc (class Dbg)
     * /libcore/dalvik/src/main/java/org/apache/harmony/dalvik/ddmc/DdmServer.java
     * /frameworks/base/core/java/android/ddm/DdmRegister.java
     * /frameworks/base/core/java/android/ddm/DdmHandleViewDebug.java
     * /frameworks/base/core/java/android/view/ViewDebug.java
     *
     * NOTE: HandleViewDebug.sendXXXGlTracing() not work,
     *       use https://github.com/google/gapid instead (based on sys/kernel/debug/tracing/trace_marker)
     */

    /** use Device can executeShellCommand */
    public static IDevice getDevice() {
        return device;
    }

    /** use Client can track heap/thread/method, and the result handled will fill to ClientData */
    public static Client getClient() {
        return client;
    }

    public static ClientData getClientData() {
        return client.getClientData();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Adb path as args[0] is needed");
            return;
        }
        mainThread.start();
        mainThread.init();

        mainThread.register(CONNECT_DEVICES, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                final AndroidDebugBridge adb = AndroidDebugBridge.createBridge(args[0], true);
                int trials;

                trials = 10;
                try {
                    while (trials > 0) {
                        Thread.sleep(50);
                        if (adb.isConnected()) {
                            break;
                        }
                        trials--;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!adb.isConnected()) {
                    JOptionPane.showMessageDialog(frame, "Couldn't connect to ADB server");
                    return;
                }

                try {
                    trials = 10;
                    while (trials > 0) {
                        Thread.sleep(50);
                        if (adb.hasInitialDeviceList()) {
                            break;
                        }
                        trials--;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!adb.hasInitialDeviceList()) {
                    JOptionPane.showMessageDialog(frame, "Couldn't initial device list");
                    return;
                }

                System.out.println("Connect to ADB server successfully");
                final IDevice[] devices = adb.getDevices();
                if (devices == null || devices.length == 0) {
                    JOptionPane.showMessageDialog(frame, "No device found");
                    return;
                }
                for (IDevice device : devices) {
                    final StringBuilder sb = new StringBuilder("===========================================\n");
                    sb.append("name: ").append(device.getName()).append(";\n");
                    sb.append("state: ").append(device.getState()).append(";\n");
                    sb.append("serialNumber: ").append(device.getSerialNumber()).append(";\n");
                    sb.append("androidVersion: ").append(device.getVersion()).append(";\n");
                    sb.append(device.isEmulator() ? "is " : "not ").append("emulator;\n");
                    try {
                        sb.append(device.isRoot() ? "is " : "not ").append("root;\n");
                    } catch (Throwable ignored) {
                    }
                    sb.append("===========================================\n");

                    System.out.println(sb.toString());
                }

                if (devices.length == 1) {
                    device = devices[0];
                } else { // more one
                    final String[] strs = new String[devices.length];
                    for (int i = 0; i < devices.length; i++) {
                        strs[i] = devices[i].getName();
                    }
                    device = showSelectDialog(frame, "Select device", strs, devices);
                }
                if (device != null) {
                    System.out.println("[Feedback]select device " + device.getName());
                }
            }
        });

        mainThread.register(DISCONNECT_DEVICES, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                AndroidDebugBridge.disconnectBridge();
                System.out.println("Disconnect done");
            }
        });

        mainThread.register(LIST_PROPERTIES, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (device != null) {
                    final StringBuilder sb = new StringBuilder("--------->\n");
                    @SuppressWarnings("deprecation")
                    final Map<String, String> properties = device.getProperties();
                    for (Map.Entry<String, String> entry: properties.entrySet()) {
                        sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append(";\n");
                    }
                    sb.append("<---------\n");
                    System.out.println(sb.toString());
                }
            }
        });

        mainThread.register(SELECT_CLIENT, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (device != null) {
                    final Client[] clients = device.getClients();
                    if (clients == null || clients.length <= 0) {
                        JOptionPane.showMessageDialog(frame, "No debuggable application found");
                        return;
                    }
                    final String[] strs = new String[clients.length];
                    System.out.println("client ****************");
                    for (int i = 0; i < clients.length; i++) {
                        strs[i] = clients[i].getClientData().getClientDescription();
                        if (strs[i] == null) {
                            strs[i] = clients[i].toString();
                        }
                        System.out.println(strs[i]);
                    }
                    client = showSelectDialog(frame, "Select application", strs, clients);
                    if (client != null) {
                        System.out.println("[Feedback]select client " + client.getClientData().getClientDescription());
                    }
                }
            }
        });

        mainThread.register(SELECT_VIEW_ROOT, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (client == null) {
                    return;
                }
                try {
                    HandleViewDebug.listViewRoots(client, new HandleViewDebug.ViewDumpHandler(
                            HandleViewDebug.CHUNK_VULW) {
                        @Override
                        protected void handleViewDebugResult(ByteBuffer data) {
                            final int nWindows = data.getInt();
                            String[] windows = new String[nWindows];
                            System.out.println("view root(window) ****************");
                            for (int i = 0; i < nWindows; ++i) {
                                windows[i] = getString(data, data.getInt());
                                System.out.println(windows[i]);
                            }
                            viewRoot = showSelectDialog(frame, "Select view root", windows, windows);
                            if (viewRoot != null) {
                                System.out.println("[Feedback]select view root(window) " + viewRoot);
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mainThread.register(SELECT_VIEW_NODE, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (client == null || viewRoot == null) {
                    return;
                }
                try {
                    HandleViewDebug.dumpViewHierarchy(client, viewRoot, false, true,
                            new HandleViewDebug.ViewDumpHandler(HandleViewDebug.CHUNK_VURT) {
                                @Override
                                protected void handleViewDebugResult(ByteBuffer byteBuffer) {
                                    byte[] bytes = new byte[byteBuffer.remaining()];
                                    byteBuffer.get(bytes);
                                    ViewNode rootViewNode = ViewNode.parseViewNode(bytes, viewRoot);
                                    if (rootViewNode != null) {
                                        System.out.println("found root view node: " + rootViewNode.toString());
                                        ArrayList<String> ids = new ArrayList<>();
                                        ArrayList<ViewNode> nodes = new ArrayList<>();
                                        ViewNode.collectViewIds(rootViewNode, ids, nodes);
                                        String[] idArray = new String[ids.size()];
                                        ViewNode[] nodeArray = new ViewNode[nodes.size()];
                                        viewNode = showSelectDialog(frame, "Select view node",
                                                ids.toArray(idArray), nodes.toArray(nodeArray));
                                        if (viewNode != null) {
                                            System.out.println("[Feedback]select view node " + viewNode.toString());
                                        }
                                    }
                                }
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mainThread.register(CAPTURE_VIEW, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (client == null || viewRoot == null || viewNode == null) {
                    return;
                }
                try {
                    HandleViewDebug.captureView(client, viewRoot, viewNode.toString(),
                            new HandleViewDebug.ViewDumpHandler(HandleViewDebug.CHUNK_VUOP) {
                                @Override
                                protected void handleViewDebugResult(ByteBuffer byteBuffer) {
                                    byte[] bytes = new byte[byteBuffer.remaining()];
                                    byteBuffer.get(bytes);
                                    File file = new File("capture_" + System.currentTimeMillis() + ".png");
                                    try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
                                        outputStream.write(bytes);
                                        System.out.println("capture done at " + file.getAbsolutePath());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mainThread.register(REFRESH_VIEW, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (client == null || viewRoot == null || viewNode == null) {
                    return;
                }
                String view = viewNode.toString();
                try {
                    HandleViewDebug.requestLayout(client, viewRoot, view);
                    System.out.println(view + " layout done");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    HandleViewDebug.invalidateView(client, viewRoot, view);
                    System.out.println(view + " invalidate done");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mainThread.register(PROFILE_VIEW, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (client == null || viewRoot == null || viewNode == null) {
                    return;
                }
                try {
                    HandleViewDebug.profileView(client, viewRoot, viewNode.toString(),
                            new HandleViewDebug.ViewDumpHandler(HandleViewDebug.CHUNK_VUOP) {
                                @Override
                                protected void handleViewDebugResult(ByteBuffer byteBuffer) {
                                    try {
                                        byte[] bytes = new byte[byteBuffer.remaining()];
                                        byteBuffer.get(bytes);
                                        boolean success = ViewNode.loadProfileDataRecursive(viewNode,
                                                new BufferedReader(new StringReader(new String(bytes))));
                                        if (success) {
                                            viewNode.setProfileRatings();
                                            viewNode.setViewCount();
                                            System.out.println(viewNode.getProfileDescription("Root:"));
                                            ArrayList<ViewNode> viewNodes = new ArrayList<>();
                                            // statistical method is incorrect
                                            ViewNode.collectRedNode(viewNode, viewNodes);
                                            for (ViewNode node : viewNodes) {
                                                if (!viewNode.equals(node)) {
                                                    System.out.println(node.getProfileDescription("Bad:"));
                                                }
                                            }
                                            System.out.println("current view profile done");
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mainThread.register(INVOKE_VIEW_METHOD, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (client == null || viewRoot == null || viewNode == null) {
                    return;
                }
                try {
                    HandleViewDebug.invokeMethod(client, viewRoot, viewNode.toString(),
                            "setBackgroundColor"/* public method */, 0xFFFF00FF/* supports primitive arguments */);
                    System.out.println("invoke view method ok");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mainThread.register(NATIVE_HEAP_TRACK, new MainThread.Callback() {
            @Override
            public void handleMessage(int what, Object data) {
                if (client == null) {
                    return;
                }
                ClientData clientData = client.getClientData();
                clientData.setNativeDebuggable(true);
                // read data from /proc/self/maps
                // handle with get_malloc_leak_info(...) in /bionic/libc/bionic/malloc_common.cpp
                boolean result = client.requestNativeHeapInformation();
                System.out.println("requestNativeHeapInformation: " + (result ? "successful" : "failed"));
                if (result) {
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ignored) {
                    }
                    // not work
                    System.out.println("getTotalNativeMemory: " + clientData.getTotalNativeMemory());
                    System.out.println("getNativeAllocationList: " + clientData.getNativeAllocationList());
                    System.out.println("getMappedNativeLibraries: " + clientData.getMappedNativeLibraries());
                }
            }
        });

        mainThread.register(DUMP_THREAD, new MainThread.Callback() {
            @Override
            public void handleMessage(final int what, final Object data) {
                if (client == null) {
                    return;
                }
                client.setThreadUpdateEnabled(true);
                int i = 8;
                while (i-- >= 0) {
                    client.requestThreadUpdate();
                    final ThreadInfo[] threads = client.getClientData().getThreads();
                    if (threads != null) {
                        for (ThreadInfo thread : threads) {
                            if ("main".equals(thread.getThreadName())) {
                                client.requestThreadStackTrace(thread.getThreadId());
                                System.out.println("MainThread[" + thread.getTid() + "] status " + thread.getStatus());
                                final StackTraceElement[] stackTrace = thread.getStackTrace();
                                if (stackTrace != null) {
                                    for (StackTraceElement element : stackTrace) {
                                        System.out.println(element);
                                    }
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException ignored) {
                    }
                }
                System.out.println("Done!");
            }
        });

        final JButton connect = new JButton("connect [0]");
        connect.addActionListener(e -> mainThread.sendMessage(CONNECT_DEVICES, null));

        final JButton disconnect = new JButton("disconnect [-1]");
        disconnect.addActionListener(e -> mainThread.sendMessage(DISCONNECT_DEVICES, null));

        final JButton listProps = new JButton("listProps [1]");
        listProps.addActionListener(e -> mainThread.sendMessage(LIST_PROPERTIES, null));

        final JButton selectClient = new JButton("selectClient [1]");
        selectClient.addActionListener(e -> mainThread.sendMessage(SELECT_CLIENT, null));

        final JButton selectViewRoot = new JButton("selectViewRoot [2]");
        selectViewRoot.addActionListener(e -> mainThread.sendMessage(SELECT_VIEW_ROOT, null));

        final JButton selectViewNode = new JButton("selectViewNode [3]");
        selectViewNode.addActionListener(e -> mainThread.sendMessage(SELECT_VIEW_NODE, null));

        final JButton captureView = new JButton("captureView [4]");
        captureView.addActionListener(e -> mainThread.sendMessage(CAPTURE_VIEW, null));

        final JButton refreshView = new JButton("refreshView [4]");
        refreshView.addActionListener(e -> mainThread.sendMessage(REFRESH_VIEW, null));

        final JButton profileView = new JButton("profileView [4]");
        profileView.addActionListener(e -> mainThread.sendMessage(PROFILE_VIEW, null));

        final JButton invokeViewMethod = new JButton("invokeViewMethod [4]");
        invokeViewMethod.addActionListener(e -> mainThread.sendMessage(INVOKE_VIEW_METHOD, null));

        final JButton nativeHeapTrack = new JButton("nativeHeapTrack [2]");
        nativeHeapTrack.addActionListener(e -> mainThread.sendMessage(NATIVE_HEAP_TRACK, null));

        final JButton dumpThread = new JButton("dumpThread [2]");
        dumpThread.addActionListener(e -> mainThread.sendMessage(DUMP_THREAD, null));

        panel.add(connect);
        panel.add(disconnect);
        panel.add(listProps);
        panel.add(selectClient);
        panel.add(selectViewRoot);
        panel.add(selectViewNode);
        panel.add(captureView);
        panel.add(refreshView);
        panel.add(profileView);
        panel.add(invokeViewMethod);
        panel.add(nativeHeapTrack);
        panel.add(dumpThread);
        frame.setContentPane(panel);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                mainThread.quit();
            }
        });
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(600, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static <E> E showSelectDialog(JFrame frame, String description, String[] itemStrs, E[] itemObjs) {
        if (itemStrs == null || itemObjs == null || itemStrs.length <= 0 || itemStrs.length != itemObjs.length) {
            throw new IllegalArgumentException();
        }

        final JComboBox<String> comboBox = new JComboBox<>(itemStrs);
        comboBox.setSelectedIndex(0);
        final JPanel panel = new JPanel();
        panel.add(comboBox);
        final JDialog dialog = new JDialog(frame, description, true);
        dialog.setContentPane(panel);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        int selectedIndex = comboBox.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= itemObjs.length) {
            selectedIndex = 0;
        }
        return itemObjs[selectedIndex];
    }

    public static class ViewNode {
        public enum ProfileRating {
            RED, YELLOW, GREEN, NONE
        };
        private static final double RED_THRESHOLD = 0.8;
        private static final double YELLOW_THRESHOLD = 0.5;

        public String viewRoot;
        public ViewNode parent;
        public List<ViewNode> children = new ArrayList<>();
        public int index;
        public int viewCount;
        public String name;
        public String hashCode;
        public Map<String, String> properties = new HashMap<>();
        public String id;
        public int left;
        public int top;
        public int width;
        public int height;
        public double measureTime;
        public double layoutTime;
        public double drawTime;
        public ProfileRating measureRating = ProfileRating.NONE;
        public ProfileRating layoutRating = ProfileRating.NONE;
        public ProfileRating drawRating = ProfileRating.NONE;

        public ViewNode(String viewRoot, ViewNode parent, String data) {
            this.viewRoot = viewRoot;
            this.parent = parent;
            index = this.parent == null ? 0 : this.parent.children.size();
            if (this.parent != null) {
                this.parent.children.add(this);
            }

            int delimIndex = data.indexOf('@');
            name = data.substring(0, delimIndex);
            data = data.substring(delimIndex + 1);
            delimIndex = data.indexOf(' ');
            hashCode = data.substring(0, delimIndex);

            if (data.length() > delimIndex + 1) {
                data = data.substring(delimIndex + 1).trim();
                int start = 0;
                boolean stop;
                do {
                    int index = data.indexOf('=', start);
                    String key = data.substring(start, index);
                    int index2 = data.indexOf(',', index + 1);
                    int length = Integer.parseInt(data.substring(index + 1, index2));
                    start = index2 + 1 + length;
                    String value = data.substring(index2 + 1, index2 + 1 + length);
                    properties.put(key, value);
                    stop = start >= data.length();
                    if (!stop) {
                        start += 1;
                    }
                } while (!stop);

                id = properties.get("mID");
                left = properties.containsKey("mLeft") ? getInt("mLeft", 0) : getInt("layout:mLeft", 0);
                top = properties.containsKey("mTop") ? getInt("mTop", 0) : getInt("layout:mTop", 0);
                width = properties.containsKey("getWidth()") ? getInt("getWidth()", 0) : getInt("layout:getWidth()", 0);
                height = properties.containsKey("getHeight()") ? getInt("getHeight()", 0) : getInt("layout:getHeight()", 0);
            }
        }

        private int getInt(String name, int defaultValue) {
            String value = properties.get(name);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }

        @Override
        public String toString() {
            return name + "@" + hashCode;
        }

        public void setProfileRatings() {
            final int N = children.size();
            if (N > 1) {
                double totalMeasure = 0;
                double totalLayout = 0;
                double totalDraw = 0;
                for (int i = 0; i < N; i++) {
                    ViewNode child = children.get(i);
                    totalMeasure += child.measureTime;
                    totalLayout += child.layoutTime;
                    totalDraw += child.drawTime;
                }
                for (int i = 0; i < N; i++) {
                    ViewNode child = children.get(i);
                    if (child.measureTime / totalMeasure >= RED_THRESHOLD) {
                        child.measureRating = ProfileRating.RED;
                    } else if (child.measureTime / totalMeasure >= YELLOW_THRESHOLD) {
                        child.measureRating = ProfileRating.YELLOW;
                    } else {
                        child.measureRating = ProfileRating.GREEN;
                    }
                    if (child.layoutTime / totalLayout >= RED_THRESHOLD) {
                        child.layoutRating = ProfileRating.RED;
                    } else if (child.layoutTime / totalLayout >= YELLOW_THRESHOLD) {
                        child.layoutRating = ProfileRating.YELLOW;
                    } else {
                        child.layoutRating = ProfileRating.GREEN;
                    }
                    if (child.drawTime / totalDraw >= RED_THRESHOLD) {
                        child.drawRating = ProfileRating.RED;
                    } else if (child.drawTime / totalDraw >= YELLOW_THRESHOLD) {
                        child.drawRating = ProfileRating.YELLOW;
                    } else {
                        child.drawRating = ProfileRating.GREEN;
                    }
                }
            }
            for (int i = 0; i < N; i++) {
                children.get(i).setProfileRatings();
            }
        }

        public void setViewCount() {
            viewCount = 1;
            final int N = children.size();
            for (int i = 0; i < N; i++) {
                ViewNode child = children.get(i);
                child.setViewCount();
                viewCount += child.viewCount;
            }
        }

        public String getProfileDescription(String prefix) {
            return prefix + " " + toString() + "{\n"
                    + "viewCount=" + viewCount + ";\n"
                    + "measureTime=" + measureTime + ";\n"
                    + "layoutTime=" + layoutTime + ";\n"
                    + "drawTime=" + drawTime + ";\n"
                    + "measureRating=" + measureRating + ";\n"
                    + "layoutRating=" + layoutRating + ";\n"
                    + "drawRating=" + drawRating + ";\n"
                    + "}";
        }

        public static ViewNode parseViewNode(byte[] bytes, String viewRoot) {
            BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes),
                    Charset.forName("UTF-8")));
            ViewNode currentNode = null;
            int currentDepth = -1;
            String line;
            try {
                while ((line = in.readLine()) != null) {
                    if ("DONE.".equalsIgnoreCase(line)) {
                        break;
                    }
                    int depth = 0;
                    while (line.charAt(depth) == ' ') {
                        depth++;
                    }
                    while (depth <= currentDepth) {
                        if (currentNode != null) {
                            currentNode = currentNode.parent;
                        }
                        currentDepth--;
                    }
                    currentNode = new ViewNode(viewRoot, currentNode, line.substring(depth));
                    currentDepth = depth;
                }
            } catch (IOException e) {
                System.err.println("Error reading view hierarchy stream: " + e.getMessage());
                return null;
            }
            if (currentNode == null) {
                return null;
            }
            while (currentNode.parent != null) {
                currentNode = currentNode.parent;
            }
            return currentNode;
        }

        public static void collectViewIds(ViewNode rootViewNode, List<String> ids, List<ViewNode> nodes) {
            ids.add(rootViewNode.toString() + "+" + String.valueOf(rootViewNode.id));
            nodes.add(rootViewNode);
            for (ViewNode child : rootViewNode.children) {
                collectViewIds(child, ids, nodes);
            }
        }

        public static ViewNode findViewById(ViewNode rootViewNode, String id) {
            id = makesureId(id);
            if (id.isEmpty()) {
                return null;
            }
            if (id.equals(rootViewNode.id)) {
                return rootViewNode;
            }
            ViewNode target;
            for (ViewNode child : rootViewNode.children) {
                if ((target = findViewById(child, id)) != null) {
                    return target;
                }
            }
            return null;
        }

        public static String makesureId(String id) {
            if (id == null || id.trim().isEmpty() || "NO_ID".equalsIgnoreCase(id)) {
                return "";
            }
            int idx = id.indexOf('+');
            if (idx >= 0) {
                id = id.substring(idx + 1);
            }
            if (id.startsWith("id/")) {
                id = id.substring(3);
            }
            return id;
        }

        private static boolean loadProfileData(ViewNode node, BufferedReader in) throws IOException {
            String line;
            if ((line = in.readLine()) == null || line.equalsIgnoreCase("-1 -1 -1") //$NON-NLS-1$
                    || line.equalsIgnoreCase("DONE.")) { //$NON-NLS-1$
                return false;
            }
            String[] data = line.split(" ");
            node.measureTime = (Long.parseLong(data[0]) / 1000.0) / 1000.0;
            node.layoutTime = (Long.parseLong(data[1]) / 1000.0) / 1000.0;
            node.drawTime = (Long.parseLong(data[2]) / 1000.0) / 1000.0;
            return true;
        }

        public static boolean loadProfileDataRecursive(ViewNode node, BufferedReader in) throws IOException {
            if (!loadProfileData(node, in)) {
                return false;
            }
            for (int i = 0; i < node.children.size(); i++) {
                if (!loadProfileDataRecursive(node.children.get(i), in)) {
                    return false;
                }
            }
            return true;
        }

        public static void collectRedNode(ViewNode root, List<ViewNode> viewNodes) {
            if (root.measureRating.equals(ProfileRating.RED)
                    || root.layoutRating.equals(ProfileRating.RED)
                    || root.drawRating.equals(ProfileRating.RED)) {
                viewNodes.add(root);
            }
            for (ViewNode child : root.children) {
                collectRedNode(child, viewNodes);
            }
        }
    }
}
