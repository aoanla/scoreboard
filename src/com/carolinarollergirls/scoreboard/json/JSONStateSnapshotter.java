package com.carolinarollergirls.scoreboard.json;

import io.prometheus.client.Histogram;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

import com.fasterxml.jackson.jr.ob.JSON;
import com.carolinarollergirls.scoreboard.core.interfaces.Game;
import com.carolinarollergirls.scoreboard.utils.BasePath;
import com.carolinarollergirls.scoreboard.utils.Logger;

public class JSONStateSnapshotter implements JSONStateListener {

    public JSONStateSnapshotter(JSONStateManager jsm, Game g) {
        this.directory = BasePath.get();
        game = g;
        pathPrefix = "ScoreBoard.Game(" + game.getId() + ")";
        jsm.register(this);
    }

    @Override
    public synchronized void sendUpdates(Map<String, Object> newState, Set<String> changed) {
        for(String key : changed) {
            if(key.startsWith(pathPrefix) && !key.endsWith("Secret")) {
                state.put(key, newState.get(key));
            }
        }
        if(writeOnNextUpdate) {
            writeOnNextUpdate = false;
            writeFile();
        }
    }

    public void writeOnNextUpdate() {
        writeOnNextUpdate = true;
    }

    public void writeFile() {
        Histogram.Timer timer = updateStateDuration.startTimer();

        File file = new File(new File(directory, "html/game-data/json"), game.getFilename() + ".json");
        File prev = new File(new File(directory, "html/game-data/json"), game.getFilename() + "_prev.json");
        file.getParentFile().mkdirs();

        File tmp = null;
        OutputStreamWriter out = null;
        try {
            // Put inside a "state" entry to match the WS.
            String json = JSON.std
                          .with(JSON.Feature.PRETTY_PRINT_OUTPUT)
                          .composeString()
                          .startObject()
                          .putObject("state", state)
                          .end()
                          .finish();
            tmp = File.createTempFile(file.getName(), ".tmp", directory);
            out = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
            out.write(json);
            out.close();
            prev.delete();
            file.renameTo(prev);
            if (tmp.renameTo(file)) {
                prev.delete();
            }
        } catch (Exception e) {
            Logger.printMessage("Error writing JSON snapshot: " + e.getMessage());
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception e) { }
            }
            if (tmp != null ) {
                try { tmp.delete(); } catch (Exception e) { }
            }
        }
        timer.observeDuration();
    }

    private File directory;
    private Game game;
    private String pathPrefix;
    private boolean writeOnNextUpdate = false;
    // Use a TreeMap so output is sorted.
    private Map<String, Object> state = new TreeMap<>();

    private static final Histogram updateStateDuration = Histogram.build()
            .name("crg_json_state_disk_snapshot_duration_seconds").help("Time spent writing JSON state snapshots to disk").register();
}
