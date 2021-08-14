package jp.ilib.mic;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.List;

public class VoipMicrophone {

    public interface DataListener {
        void OnRead(float[] data, int size);
    }

    public boolean UseBluetooth = false;
    public boolean UseAcousticEchoCanceler = true;
    public boolean UseAutomaticGainControl = true;
    public boolean UseNoiseSuppressor = true;

    Context mContext;
    AudioManager mAudioManager;
    AudioRecord mAudioRecord;
    AcousticEchoCanceler mAcousticEchoCanceler;
    AutomaticGainControl mAutomaticGainControl;
    NoiseSuppressor mNoiseSuppressor;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothHeadset mBluetoothHeadset;
    BluetoothDevice mBluetoothDevice;
    boolean mClosed;
    Handler mHandler = new Handler(Looper.getMainLooper());

    int mPrevAudioMode;
    boolean mPrevSpeakerphoneOn = false;
    DataListener mListener;
    Thread mThread;
    BroadcastReceiver mBluetoothReceiver;
    BluetoothProfile.ServiceListener mBluetoothProfileListener ;
    BroadcastReceiver mHeadsetPlugReceiver;
    AudioDeviceCallback mAudioDeviceCallback ;

    public  VoipMicrophone()
    {
        createReceiver();
    }

    public void start(Context context, DataListener listener) {
        mContext = context;
        mListener = listener;

        mAudioManager = (AudioManager) mContext.getSystemService(Service.AUDIO_SERVICE);
        mPrevAudioMode = mAudioManager.getMode();
        mPrevSpeakerphoneOn = mAudioManager.isSpeakerphoneOn();
        setAudioMode();
        if (UseBluetooth) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBluetoothAdapter.getProfileProxy(mContext, mBluetoothProfileListener, BluetoothProfile.HEADSET);
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            mContext.registerReceiver(mBluetoothReceiver, filter);
        }
        {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_HEADSET_PLUG);
            mContext.registerReceiver(mHeadsetPlugReceiver, filter);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, null);
            }
        }
        int sampleRate = 48000;
        int channelMask = AudioFormat.CHANNEL_IN_MONO;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelMask).build();
            mAudioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize).build();
        } else {
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        }
        if (canAcousticEchoCanceler()) {
            mAcousticEchoCanceler = AcousticEchoCanceler.create(mAudioRecord.getAudioSessionId());
            mAcousticEchoCanceler.setEnabled(true);
        }
        if (canAutomaticGainControl()) {
            mAutomaticGainControl = AutomaticGainControl.create(mAudioRecord.getAudioSessionId());
            mAutomaticGainControl.setEnabled(true);
        }
        if (canNoiseSuppressor()) {
            mNoiseSuppressor = NoiseSuppressor.create(mAudioRecord.getAudioSessionId());
            mNoiseSuppressor.setEnabled(true);
        }
        mAudioRecord.startRecording();
        mThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            short[] buf = new short[bufferSize/2];
            float[] data = new float[buf.length];
            while (!mClosed) {
                int size = mAudioRecord.read(buf,0,buf.length);
                if(size < 0) {
                    break;
                } else if (size == 0) {
                    continue;
                } else if (size <= buf.length) {
                    for(int i = 0;i<size;i++) {
                        data[i] = buf[i] / 32767.0f;
                    }
                    mListener.OnRead(data, size);
                }
            }
            mAudioRecord.stop();
        });
        mThread.start();
    }

    public void close() {
        mClosed = true;
        if(mAudioRecord != null) {
            mAudioRecord.release();;
            mAudioRecord = null;
        }
        if (UseBluetooth) {
            mContext.unregisterReceiver(mBluetoothReceiver);
            mAudioManager.setBluetoothScoOn(false);
            if (mBluetoothDevice != null) {
                mBluetoothHeadset.stopVoiceRecognition(mBluetoothDevice);
                mBluetoothDevice = null;
            }
            if (mBluetoothHeadset != null) {
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
                mBluetoothHeadset = null;
            }
        }
        mContext.unregisterReceiver(mHeadsetPlugReceiver);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        }
        mAudioManager.setMode(mPrevAudioMode);
        mAudioManager.setSpeakerphoneOn(mPrevSpeakerphoneOn);
    }

    public void setAcousticEchoCancelerEnabled(boolean enabled)
    {
        if(mAcousticEchoCanceler != null) {
            mAcousticEchoCanceler.setEnabled(enabled);
        }
    }

    public boolean getAcousticEchoCancelerEnabled()
    {
        if(mAcousticEchoCanceler != null) {
            return mAcousticEchoCanceler.getEnabled();
        }
        return false;
    }

    public void setAutomaticGainControlEnabled(boolean enabled)
    {
        if(mAutomaticGainControl != null) {
            mAutomaticGainControl.setEnabled(enabled);
        }
    }

    public boolean getAutomaticGainControlEnabled()
    {
        if(mAutomaticGainControl != null) {
            return mAutomaticGainControl.getEnabled();
        }
        return false;
    }

    public void setNoiseSuppressorEnabled(boolean enabled)
    {
        if(mNoiseSuppressor != null) {
            mNoiseSuppressor.setEnabled(enabled);
        }
    }

    public boolean getNoiseSuppressorEnabled()
    {
        if(mNoiseSuppressor != null) {
            return mNoiseSuppressor.getEnabled();
        }
        return false;
    }

    void setAudioMode() {
        if (mClosed) return;
        if (isHeadset()) {
            mAudioManager.setMode(AudioManager.MODE_NORMAL);
            mAudioManager.setSpeakerphoneOn(false);
        } else {
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mAudioManager.setSpeakerphoneOn(true);
        }
    }

    boolean isHeadset() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (int i = 0; i < devices.length; i++) {
                switch (devices[i].getType()) {
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    case AudioDeviceInfo.TYPE_USB_DEVICE:
                    case AudioDeviceInfo.TYPE_USB_HEADSET:
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                        //case AudioDeviceInfo.TYPE_BLE_HEADSET:
                        return true;
                }
            }
            return false;
        } else {
            return mAudioManager.isWiredHeadsetOn() || mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn();
        }
    }

    boolean canAcousticEchoCanceler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return false;
        }
        return UseAcousticEchoCanceler && AcousticEchoCanceler.isAvailable();
    }

    boolean canAutomaticGainControl() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return false;
        }
        return UseAutomaticGainControl && AutomaticGainControl.isAvailable();
    }

    boolean canNoiseSuppressor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return false;
        }
        return UseNoiseSuppressor && NoiseSuppressor.isAvailable();
    }

    void createReceiver(){

         mBluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null || mClosed) {
                    return;
                }
                if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    mBluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mClosed) return;
                            if (mBluetoothHeadset != null && mBluetoothDevice != null) {
                                mBluetoothHeadset.startVoiceRecognition(mBluetoothDevice);
                            }
                        }
                    }, 1000);
                }
            }
        };

        mBluetoothProfileListener = new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int i, BluetoothProfile bluetoothProfile) {
                mBluetoothHeadset = (BluetoothHeadset) bluetoothProfile;
                List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
                if (devices.size() > 0) {
                    mBluetoothDevice = devices.get(0);
                    mBluetoothHeadset.startVoiceRecognition(mBluetoothDevice);
                }
            }

            @Override
            public void onServiceDisconnected(int i) {
                if (mBluetoothHeadset != null && mBluetoothDevice != null) {
                    mBluetoothHeadset.stopVoiceRecognition(mBluetoothDevice);
                }
                mBluetoothDevice = null;
            }
        };

        mHeadsetPlugReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null || mClosed) {
                    return;
                }
                if (action == Intent.ACTION_HEADSET_PLUG) {
                    setAudioMode();
                }
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mAudioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    setAudioMode();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    setAudioMode();
                }
            };
        }
    }

}
