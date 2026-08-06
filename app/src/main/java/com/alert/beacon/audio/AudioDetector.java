package com.alert.beacon.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.alert.beacon.data.TriggerManager;

import java.util.ArrayList;
import java.util.Locale;

public class AudioDetector {
    private static final String TAG = "AudioDetector";
    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private final TriggerManager triggerManager;
    private boolean isListening = false;
    private boolean isSpeechRecognizerActive = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Silent monitoring variables (VAD - Voice Activity Detection)
    private AudioRecord audioRecord;
    private Thread monitoringThread;
    private static final int SAMPLE_RATE = 8000;
    private static final int THRESHOLD = 2500; // Adjust loudness threshold

    public interface OnTriggerDetectedListener {
        void onTriggerDetected();
    }

    private final OnTriggerDetectedListener listener;

    public AudioDetector(Context context, OnTriggerDetectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.triggerManager = new TriggerManager(context);
    }

    public void startListening() {
        if (isListening) return;
        isListening = true;
        startSilentMonitoring();
    }

    public void stopListening() {
        isListening = false;
        stopSilentMonitoring();
        stopSpeechRecognizer();
    }

    private void startSilentMonitoring() {
        if (monitoringThread != null) return;

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE) return;

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioRecord.startRecording();
        } catch (SecurityException e) {
            Log.e(TAG, "Mic permission missing for silent monitor");
            return;
        }

        monitoringThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isListening) {
                if (audioRecord == null) break;
                
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0 && !isSpeechRecognizerActive) {
                    double amplitude = 0;
                    for (short s : buffer) amplitude += Math.abs(s);
                    amplitude /= read;

                    if (amplitude > THRESHOLD) {
                        Log.d(TAG, "Loud sound detected (" + amplitude + "), triggering speech recognizer");
                        mainHandler.post(this::triggerSpeechRecognizer);
                        // Sleep a bit to let recognition happen
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    }
                }
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        });
        monitoringThread.start();
    }

    private void stopSilentMonitoring() {
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
        monitoringThread = null;
    }

    private void triggerSpeechRecognizer() {
        if (isSpeechRecognizerActive || !isListening) return;
        isSpeechRecognizerActive = true;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Log.d(TAG, "Speech ready"); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) { 
                Log.e(TAG, "Recognition error: " + error);
                isSpeechRecognizerActive = false;
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    String trigger = triggerManager.getVoiceTriggerPhrase();
                    for (String result : matches) {
                        if (result.toLowerCase().contains(trigger.toLowerCase())) {
                            if (listener != null) listener.onTriggerDetected();
                            break;
                        }
                    }
                }
                isSpeechRecognizerActive = false;
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private void stopSpeechRecognizer() {
        isSpeechRecognizerActive = false;
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        });
    }
}
