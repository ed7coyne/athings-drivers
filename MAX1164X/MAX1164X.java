package com.google.android.things.contrib.driver.max1164x;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

/**
 * Driver for the MAX11646, MAX11647 I2C ADC.
 * https://datasheets.maximintegrated.com/en/ds/MAX11646-MAX11647.pdf
 */

public class MAX1164X {
    private static final String TAG = MAX1164X.class.getSimpleName();
    public enum ScanSelection {
        FROM_0_TO_INPUT,
        CONVERT_INPUT_8X,
        CONVERT_INPUT,
    }

    public enum DifferentialMeasurementMode {
        SINGLE_ENDED,
        DIFFERENTIAL
    }

    public enum ReferenceVoltage {
        VDD,
        REFERENCE_INPUT,
        INTERNAL_REFERENCE_ALWAYS_OFF,
        INTERNAL_REFERENCE_ALWAYS_ON,
        REFERENCE_OUTPUT_ALWAYS_OFF,
        REFERENCE_OUTPUT_ALWAYS_ON,
    }

    public enum Clock {
        INTERNAL,
        EXTERNAL
    }

    public enum DifferentialMode {
        BIPOLAR,
        UNIPOLAR
    }

    private static class Setup {

        public ReferenceVoltage referenceVoltage = ReferenceVoltage.VDD;
        public Clock clock = Clock.INTERNAL;
        public DifferentialMode differentialMode = DifferentialMode.UNIPOLAR;
        public boolean resetConfig = false;

        public byte getByte() {
            byte out = bit(7); // bit 7 defines this as the setup byte and now the config.

            if (referenceVoltage == ReferenceVoltage.REFERENCE_INPUT) {
                out |= bit(5);
            } else if (referenceVoltage == ReferenceVoltage.INTERNAL_REFERENCE_ALWAYS_OFF) {
                out |= bit(6);
            } else if (referenceVoltage == ReferenceVoltage.INTERNAL_REFERENCE_ALWAYS_ON) {
                out |= bit(6) | bit(4);
            } else if (referenceVoltage == ReferenceVoltage.REFERENCE_OUTPUT_ALWAYS_OFF) {
                out |= bit(6) | bit(5);
            } else if (referenceVoltage == ReferenceVoltage.REFERENCE_OUTPUT_ALWAYS_ON) {
                out |= bit(6) | bit(5) | bit(4);
            }

            if (clock == Clock.EXTERNAL) {
                out |= bit(3);
            }

            if (differentialMode == DifferentialMode.BIPOLAR) {
                out |= bit(2);
            }

            if (resetConfig) {
                out |= bit(1);
            }

            return out;
        }

    }

    private static class Configuration {

        public int channel;
        public ScanSelection scanSelection = ScanSelection.FROM_0_TO_INPUT;
        public DifferentialMeasurementMode mode = DifferentialMeasurementMode.SINGLE_ENDED;

        public byte getByte() {
            byte out = 0;

            if (scanSelection == ScanSelection.CONVERT_INPUT_8X) {
                out |= bit(5);
            } else if (scanSelection == ScanSelection.CONVERT_INPUT) {
                out |= bit(5) | bit(6);
            }

            if (channel == 1) {
                out |= bit(1);
            } else if (channel > 1) {
                throw new UnsupportedOperationException("Only two channels present, 0 indexed.");
            }

            if (mode == DifferentialMeasurementMode.DIFFERENTIAL) {
                out |= bit(0);
            }

            return out;
        }

    }

    private I2cDevice mDevice;

    // This is non-configurable.
    private static final int I2C_ADDRESS = 0x36;

    private Setup mSetup = new Setup();
    private Configuration mConfig = new Configuration();

    // The difference between the MAX11646 and MAX11647 is whether it is a 5V or 3V part.
    public enum Voltage {
        _5V(5.0, 4.096),
        _3V(3.0, 2.048);

        private final double mVoltage;
        private final double mInternalReference;
        Voltage(double voltage, double internalReferenceVoltage) {
            mVoltage = voltage;
            mInternalReference = internalReferenceVoltage;
        }
        public double getVoltage() {
            return mVoltage;
        }
        public double getInternalReferenceVoltage() {
            return mInternalReference;
        }
    };

    public class Value {
        final int rawValue;

        public Value(int rawValue) {
            this.rawValue = rawValue;
        }

        public int getRawValue() {
            return rawValue;
        }

        public double getResistance(int resistorValueOhm) {
            return resistorValueOhm * ((1023.0f / rawValue) - 1);
        }

        public double getVoltage() {
            final double voltPerUnit = mVoltage.getVoltage() / 1024;
            return rawValue * voltPerUnit;
        }
    }


    private Voltage mVoltage;
    public MAX1164X(Voltage voltage) {
        mVoltage = voltage;
    }

    public void setScanMode(ScanSelection scanMode) {
        mConfig.scanSelection = scanMode;
    }

    public void setClock(Clock clock) {
        mSetup.clock = clock;
    }

    public void open() throws IOException {
        mDevice = PeripheralManager.getInstance().openI2cDevice("I2C1", I2C_ADDRESS);

        mDevice.write(new byte[]{mSetup.getByte(), mConfig.getByte()}, 2);
    }

    public Value readValue() throws IOException {
        byte[] buffer = new byte[2];
        mDevice.read(buffer, 2);
        // Only the bottom 10 bits are valid so we mask it to 0x3FF.
        return new Value(makeShort(buffer[0], buffer[1]) & 0x3FF);
    }

    private static short makeShort(int high_byte, int low_byte) {
        return (short)(((high_byte << 8) | (low_byte & 0xFF)) & 0xFFFF);
    }

    private static byte bit(int bit) {
        return (byte)(1 << bit);
    }
}
