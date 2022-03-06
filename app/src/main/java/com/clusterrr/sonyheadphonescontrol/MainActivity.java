package com.clusterrr.sonyheadphonescontrol;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Switch;

import com.twofortyfouram.spackle.bundle.BundleScrubber;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
    int mode = 0;
    int volume = 20;
    boolean voice = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        Bundle bundle = null;
        if (intent != null) {
            BundleScrubber.scrub(intent);
            bundle = intent.getBundleExtra(TaskerFireReceiver.EXTRA_BUNDLE);
        }
        if (bundle != null) {
            BundleScrubber.scrub(bundle);
            int mode = bundle.getInt(TaskerFireReceiver.EXTRA_STRING_MODE, 0);
            switch (mode) {
                case 0:
                    ((RadioButton) findViewById(R.id.radioButtonDisable)).setChecked(true);
                    break;
                case 1:
                    ((RadioButton) findViewById(R.id.radioButtonNoiseCancelling)).setChecked(true);
                    break;
                case 2:
                    ((RadioButton) findViewById(R.id.radioButtonWindCancelling)).setChecked(true);
                    break;
                case 3:
                    int volume = bundle.getInt(TaskerFireReceiver.EXTRA_STRING_VOLUME, 20);
                    boolean voice = bundle.getBoolean(TaskerFireReceiver.EXTRA_STRING_VOICE, false);
                    ((RadioButton) findViewById(R.id.radioButtonAmbientSound)).setChecked(true);
                    ((SeekBar) findViewById(R.id.seekBarVolume)).setProgress(volume - 1);
                    ((Switch) findViewById(R.id.switchVoiceOptimized)).setChecked(voice);
            }
        }

        ((RadioButton) findViewById(R.id.radioButtonDisable)).setOnCheckedChangeListener(this);
        ((RadioButton) findViewById(R.id.radioButtonNoiseCancelling)).setOnCheckedChangeListener(this);
        ((RadioButton) findViewById(R.id.radioButtonWindCancelling)).setOnCheckedChangeListener(this);
        ((RadioButton) findViewById(R.id.radioButtonAmbientSound)).setOnCheckedChangeListener(this);
        ((SeekBar) findViewById(R.id.seekBarVolume)).setOnSeekBarChangeListener(this);
        ((Switch) findViewById(R.id.switchVoiceOptimized)).setOnCheckedChangeListener(this);

        ((Button) findViewById(R.id.buttonTest)).setOnClickListener(this);
        ((Button) findViewById(R.id.buttonSave)).setOnClickListener(this);

        if (getCallingActivity() == null) // standalone mode
        {
            ((Button) findViewById(R.id.buttonTest)).setText(R.string.apply);
            ((Button) findViewById(R.id.buttonSave)).setVisibility(View.GONE);
        }

        saveSettings();
    }

    void saveSettings() {
        String blurb = "";

        if (((RadioButton) findViewById(R.id.radioButtonDisable)).isChecked()) {
            mode = TaskerFireReceiver.KEY_OFF;
            blurb = ((RadioButton) findViewById(R.id.radioButtonDisable)).getText().toString();
        } else if (((RadioButton) findViewById(R.id.radioButtonNoiseCancelling)).isChecked()) {
            mode = TaskerFireReceiver.KEY_NOISE_CANCELLING;
            blurb = ((RadioButton) findViewById(R.id.radioButtonNoiseCancelling)).getText().toString();
        } else if (((RadioButton) findViewById(R.id.radioButtonWindCancelling)).isChecked()) {
            mode = TaskerFireReceiver.KEY_WIND_CANCELLING;
            blurb = ((RadioButton) findViewById(R.id.radioButtonWindCancelling)).getText().toString();
        } else if (((RadioButton) findViewById(R.id.radioButtonAmbientSound)).isChecked()) {
            mode = TaskerFireReceiver.KEY_AMBIENT_SOUND;
            volume = ((SeekBar) findViewById(R.id.seekBarVolume)).getProgress() + 1;
            voice = ((Switch) findViewById(R.id.switchVoiceOptimized)).isChecked();
            blurb = ((RadioButton) findViewById(R.id.radioButtonAmbientSound)).getText().toString() +
                    ", volume=" + volume + (voice ? ", voice optimized" : "");
        }

        Intent resultIntent = new Intent();
        final Bundle resultBundle = new Bundle();
        resultBundle.putInt(TaskerFireReceiver.EXTRA_STRING_MODE, mode);
        resultBundle.putInt(TaskerFireReceiver.EXTRA_STRING_VOLUME, volume);
        resultBundle.putBoolean(TaskerFireReceiver.EXTRA_STRING_VOICE, voice);
        resultIntent.putExtra(TaskerFireReceiver.EXTRA_BUNDLE, resultBundle);
        resultIntent.putExtra(TaskerFireReceiver.EXTRA_STRING_BLURB, blurb);
        setResult(RESULT_OK, resultIntent);

        ((SeekBar) findViewById(R.id.seekBarVolume)).setEnabled(mode == 3);
        ((Switch) findViewById(R.id.switchVoiceOptimized)).setEnabled(mode == 3);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonTest:
                TaskerFireReceiver.execute(this, null, mode, volume, voice);
                break;
            case R.id.buttonSave:
                finish();
                break;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        saveSettings();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        saveSettings();
    }
}
