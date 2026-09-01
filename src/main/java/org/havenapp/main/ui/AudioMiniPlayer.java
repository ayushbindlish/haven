package org.havenapp.main.ui;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import org.havenapp.main.R;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tiny local-file audio player (play/pause + seek + elapsed time) wired to the controls
 * inside {@code item_audio.xml}. Replaces the unmaintained {@code net.the4thdimension:audio-wife}
 * with a plain {@link MediaPlayer}.
 */
public final class AudioMiniPlayer {

    private static final Set<AudioMiniPlayer> ACTIVE =
            Collections.newSetFromMap(new WeakHashMap<>());

    private final MediaPlayer mp = new MediaPlayer();
    private final ImageButton playBtn;
    private final SeekBar seek;
    private final TextView time;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean prepared;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!prepared) return;
            try {
                seek.setProgress(mp.getCurrentPosition());
                time.setText(fmt(mp.getCurrentPosition()) + " / " + fmt(mp.getDuration()));
            } catch (Exception ignored) {
            }
            if (mp.isPlaying()) handler.postDelayed(this, 250);
        }
    };

    private AudioMiniPlayer(View itemView, File file) {
        playBtn = itemView.findViewById(R.id.aw_play);
        seek = itemView.findViewById(R.id.aw_seek);
        time = itemView.findViewById(R.id.aw_time);

        seek.setProgress(0);
        time.setText("00:00 / 00:00");
        playBtn.setImageResource(android.R.drawable.ic_media_play);

        try {
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnPreparedListener(m -> {
                prepared = true;
                seek.setMax(m.getDuration());
                time.setText("00:00 / " + fmt(m.getDuration()));
            });
            mp.setOnCompletionListener(m -> {
                m.seekTo(0);
                seek.setProgress(0);
                playBtn.setImageResource(android.R.drawable.ic_media_play);
            });
            mp.prepareAsync();
        } catch (Exception e) {
            playBtn.setEnabled(false);
        }

        playBtn.setOnClickListener(v -> {
            if (!prepared) return;
            if (mp.isPlaying()) {
                mp.pause();
                playBtn.setImageResource(android.R.drawable.ic_media_play);
            } else {
                mp.start();
                playBtn.setImageResource(android.R.drawable.ic_media_pause);
                handler.post(tick);
            }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && prepared) mp.seekTo(progress);
            }

            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        ACTIVE.add(this);
    }

    /** Bind (or rebind) the controls in {@code itemView} to {@code file}. */
    public static AudioMiniPlayer bind(View itemView, File file) {
        return new AudioMiniPlayer(itemView, file);
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        try {
            mp.reset();
            mp.release();
        } catch (Exception ignored) {
        }
        prepared = false;
        ACTIVE.remove(this);
    }

    /** Release every live player (call from the list adapter's teardown). */
    public static void releaseAll() {
        for (AudioMiniPlayer p : ACTIVE.toArray(new AudioMiniPlayer[0])) {
            p.release();
        }
    }

    private static String fmt(int ms) {
        long s = TimeUnit.MILLISECONDS.toSeconds(ms);
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
