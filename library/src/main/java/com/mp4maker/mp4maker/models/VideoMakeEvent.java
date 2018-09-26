package com.mp4maker.mp4maker.models;

public class VideoMakeEvent {
    enum EVENT_TYPE {
        IMAGE_LOADING_START,
        IMAGE_LOADING_PROCESSING,
        IMAGE_LOADING_END,
        VIDEO_PROCESSING_START,
        VIDEO_PROCESSING,
        VIDEO_PROCESSING_END,
    }

    EVENT_TYPE type;
    Object meta;

    class ImageLoadData {
        int totalNum;
        int currentIndex;
        int totalLoadedNum;

        public ImageLoadData(int total, int current, int totalloaded) {
            this.totalNum = total;
            this.currentIndex = current;
            this.totalLoadedNum = totalloaded;
        }
    }

    class VideoProcessData {
        int totalSnapshots;
        int processedSnapshots;

        public VideoProcessData(int total, int processed) {
            this.totalSnapshots = total;
            this.processedSnapshots = processed;
        }
    }
}
