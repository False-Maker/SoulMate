package mit.alibaba.nuidemo;

public interface AudioPlayerCallback {
    public void playStart();
    public void playOver();
    public void playSoundLevel(int level);
    default public void playInfo(String info) {
        // 默认实现（可以为空）
    }
}
