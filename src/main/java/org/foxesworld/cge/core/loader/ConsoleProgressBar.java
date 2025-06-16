package org.foxesworld.cge.core.loader;

public class ConsoleProgressBar implements AssetProgressListener {
    @Override
    public void onProgress(String assetType, int loaded, int total) {
        int percent = total > 0 ? (int) ((loaded * 100.0) / total) : 100;
        int barLen = 30;
        int complete = percent * barLen / 100;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLen; i++) bar.append(i < complete ? "#" : "-");
        bar.append("]");
        System.out.printf("\r%s %s %d/%d %3d%%", assetType, bar, loaded, total, percent);
        if (loaded == total) System.out.println();
    }
}