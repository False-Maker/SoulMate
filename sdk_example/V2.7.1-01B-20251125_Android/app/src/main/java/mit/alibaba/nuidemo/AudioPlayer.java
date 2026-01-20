package mit.alibaba.nuidemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.util.concurrent.LinkedBlockingQueue;

public class AudioPlayer {

    public enum PlayState {
        idle,
        playing,
        pause,
        release
    }

    private static final String TAG = "AudioPlayer";

    private int SAMPLE_RATE = 16000;
    private String ENCODE_TYPE = "pcm";
    private boolean isFinishSend = false;
    private AudioPlayerCallback audioPlayerCallback;
    private LinkedBlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue();
    private PlayState playState;
    private byte[] tempData;
    private Thread ttsPlayerThread;

    private long total_bytes = 0;
    private long played_bytes = 0;
    private long begin_time = 0;
    private long end_time = 0;

    final static int magnification = 2;

    // 初始化播放器
    // 此处仅使用Android系统自带的AudioTrack进行音频播放Demo演示, 客户可根据自己需要替换播放器
    // 默认采样率为16000、单通道、16bit pcm格式
    private int iMinBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT) * magnification;
    private AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            iMinBufSize, AudioTrack.MODE_STREAM);


    AudioPlayer(AudioPlayerCallback callback) {
        Log.i(TAG,"Audio Player init!");
        playState = PlayState.idle;
        if (audioTrack == null) {
            Log.e(TAG, "AudioTrack is uninited!! new again...");
            iMinBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT) * magnification;
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    iMinBufSize, AudioTrack.MODE_STREAM);
        }
        if (audioTrack == null) {
            Log.e(TAG, "AudioTrack new failed ...");
        }
        audioTrack.play();
        audioPlayerCallback = callback;

        ttsPlayerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (playState != PlayState.release) {
                    if (playState == PlayState.playing) {
                        if (audioQueue.size() == 0) {
                            if (isFinishSend) {
                                audioPlayerCallback.playOver();
                                isFinishSend = false;
                                total_bytes = 0;
                                played_bytes = 0;
                                begin_time = 0;
                                end_time = 0;
                            } else {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            continue;
                        }
                        try {
                            tempData = audioQueue.take();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

//                        int sound_level = calculateRMSLevel(tempData);
//                        Log.i(TAG,"sound_level: " + sound_level);
//                        audioPlayerCallback.playSoundLevel(sound_level);

                        begin_time = played_bytes * 1000 / (SAMPLE_RATE * 2 * 1);
                        played_bytes += tempData.length;
                        end_time = played_bytes * 1000 / (SAMPLE_RATE * 2 * 1);
                        long total_time = total_bytes * 1000 / (SAMPLE_RATE * 2 * 1);
                        long duration_ms = end_time - begin_time;
                        JSONObject obj = new JSONObject();
                        obj.put("total_bytes", total_bytes);
                        obj.put("played_bytes", played_bytes);
                        obj.put("begin_time", begin_time);
                        obj.put("end_time", end_time);
                        obj.put("total_time", total_time);
                        obj.put("duration_ms", duration_ms);
                        String info = obj.toJSONString();
                        audioPlayerCallback.playInfo(info);

                        audioTrack.write(tempData, 0, tempData.length);
                    } else {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } // while
                Log.i(TAG,"exit ttsPlayerThread");
            }
        });
    }

    public void setAudioData(byte[] data) {
        total_bytes += data.length;
        audioQueue.offer(data);
        //非阻塞
    }

    public void isFinishSend(boolean isFinish) {
        isFinishSend = isFinish;
        Log.i(TAG,"Player isFinishSend:" + isFinishSend);
    }

    public void play() {
        if (!ttsPlayerThread.isAlive()) {
            ttsPlayerThread.start();
        }
        playState = PlayState.playing;
        Log.i(TAG,"Player playState:" + playState);
        isFinishSend = false;
        if (audioTrack != null) {
            audioTrack.play();
        }
        audioPlayerCallback.playStart();
    }

    public void stop() {
        playState = PlayState.idle;
        Log.i(TAG,"stop-playState :" + playState);
        audioQueue.clear();
        total_bytes = 0;
        played_bytes = 0;
        begin_time = 0;
        end_time = 0;
        if (audioTrack != null) {
            audioTrack.flush();
            audioTrack.pause();
            audioTrack.stop();
        }
    }

    public void pause() {
        playState = PlayState.pause;
        if (audioTrack != null) {
            audioTrack.pause();
        }
    }

    public void resume() {
        if (audioTrack != null) {
            audioTrack.play();
        }
        playState = PlayState.playing;
    }

    public void initAudioTrack(int samplerate) {
        // 初始化播放器
        // 此处仅使用Android系统自带的AudioTrack进行音频播放Demo演示, 客户可根据自己需要替换播放器
        // 默认采样率为16000、单通道、16bit pcm格式
        Log.i(TAG,"initAudioTrack audioTrack");
        int iMinBufSize = AudioTrack.getMinBufferSize(samplerate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT) * magnification;
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, samplerate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                iMinBufSize, AudioTrack.MODE_STREAM);
        if (audioTrack == null) {
            Log.e(TAG, "new AudioTrack failed with sr:" + samplerate + " and encode_type:" + ENCODE_TYPE);
        }
    }

    public void releaseAudioTrack(boolean finish) {
        if (audioTrack != null) {
            audioTrack.stop();
            if (finish) {
                playState = PlayState.release;
            }
            audioTrack.release();
            Log.i(TAG,"releaseAudioTrack audioTrack released");
        }
        audioTrack = null;
    }

    public void releaseAudioTrack() {
        if (audioTrack != null) {
            audioTrack.stop();
            playState = PlayState.release;
            audioTrack.release();
            Log.i(TAG,"releaseAudioTrack audioTrack released");
        }
        audioTrack = null;
    }

    public void setSampleRate(int sampleRate) {
        if (SAMPLE_RATE != sampleRate) {
            releaseAudioTrack(false);
            initAudioTrack(sampleRate);
            SAMPLE_RATE = sampleRate;
        }
    }

    public void setEncodeType(String type) {
//        int encode_type = ENCODE_TYPE;
//        if (type.equals("mp3")) {
//            encode_type = AudioFormat.ENCODING_MP3;
//        } else {
//            encode_type = AudioFormat.ENCODING_PCM_16BIT;
//        }
//        if (encode_type != ENCODE_TYPE) {
//            ENCODE_TYPE = encode_type;
//            releaseAudioTrack();
//            initAudioTrack(SAMPLE_RATE);
//        }
    }

    // 计算给定PCM音频数据的RMS值
    private int calculateRMSLevel(byte[] audioData) {
        // 将byte数组转换为short数组（假设是16位PCM，小端序）
        short[] shorts = new short[audioData.length / 2];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = (short) ((audioData[i * 2] & 0xFF) | (audioData[i * 2 + 1] << 8));
        }

        // 计算平均平方值
        double rms = 1.0;
        for (short s : shorts) {
            rms += (double)Math.abs(s);
        }
        rms = rms / shorts.length;

        // 计算分贝值
        double db = 20 * Math.log10(rms);
        db = db * 160 / 90 - 160;
        if (db > 0.0) {
            db = 0.0;
        } else if (db < -160.0) {
            db = -160;
        }

        // level值
        int level = (int)((db + 160) * 100 / 160);
        return level;
    }
}
