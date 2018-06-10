package com.chengxiang.i2cdemo;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REGISTER_TEMPERATURE_CALIBRATION_1 = 0x88;
    private static final int REGISTER_TEMPERATURE_CALIBRATION_2 = 0x8A;
    private static final int REGISTER_TEMPERATURE_CALIBRATION_3 = 0x8C;

    private static final int REGISTER_TEMPERATURE_RAW_VALUE_START = 0xFA;
    private static final int REGISTER_TEMPERATURE_RAW_VALUE_SIZE = 3;

    private TextView temperatureTextView;

    private I2cDevice i2cDevice;

    private final short[] calibrationData = new short[3];

    private static final String I2C_ADDRESS = "I2C1";
    private static final int TEMPERATURE_SENSOR_SLAVE = 0x77;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        temperatureTextView =  findViewById(R.id.temperature);
        PeripheralManager manager = PeripheralManager.getInstance();
        try {
            i2cDevice = manager.openI2cDevice(I2C_ADDRESS, TEMPERATURE_SENSOR_SLAVE);
            calibrationData[0] = i2cDevice.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_1);
            calibrationData[1] = i2cDevice.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_2);
            calibrationData[2] = i2cDevice.readRegWord(REGISTER_TEMPERATURE_CALIBRATION_3);

            byte[] data = new byte[REGISTER_TEMPERATURE_RAW_VALUE_SIZE];
            i2cDevice.readRegBuffer(REGISTER_TEMPERATURE_RAW_VALUE_START, data, REGISTER_TEMPERATURE_RAW_VALUE_SIZE);
            if (data.length != 0) {
                float temperature = compensateTemperature(readSample(data));
                temperatureTextView.setText("temperature:" + temperature);
                Log.v(TAG,"temperature:" + temperature);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (i2cDevice != null) {
            try {
                i2cDevice.close();
                i2cDevice = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private int readSample(byte[] data) {
        // msb[7:0] lsb[7:0] xlsb[7:4]
        int msb = data[0] & 0xff;
        int lsb = data[1] & 0xff;
        int xlsb = data[2] & 0xf0;
        // Convert to 20bit integer
        return (msb << 16 | lsb << 8 | xlsb) >> 4;
    }

    private float compensateTemperature(int rawTemp) {
        float digT1 = calibrationData[0];
        float digT2 = calibrationData[1];
        float digT3 = calibrationData[2];
        float adcT = (float) rawTemp;

        float varX1 = adcT / 16384f - digT1 / 1024f;
        float varX2 = varX1 * digT2;

        float varY1 = adcT / 131072f - digT1 / 8192f;
        float varY2 = varY1 * varY1;
        float varY3 = varY2 * digT3;

        return (varX2 + varY3) / 5120f;
    }
}
