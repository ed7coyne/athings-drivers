package com.google.android.things.contrib.driver.mcp23008;

import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

/**
 * Interfaces with the MCP23008 port expander to access GPIO over I2C.
 */

public class MCP23008 {
    public enum Direction {
        INPUT,
        OUTPUT
    }

    public enum CompareTarget {
        DEFVAL,
        PREVIOUS
    }

    public enum Polarity {
        NEGATED,
        NORMAL
    }

    public enum InterruptPinPolarity {
        ACTIVE_HIGH,
        ACTIVE_LOW
    }

    public enum InterruptPinMode {
        // Overrides the Interrupt Polarity.
        OPEN_DRAIN,

        // Controlled by Interrupt Polarity.
        ACTIVE_DRIVER
    }

    public static class Values {
        private final byte mValue;

        public Values(byte value) {
            mValue = value;
        }

        public boolean getPin(int pin) {
            return (mValue & bit(pin)) != 0;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Values{");
            sb.append(
                    String.format("%8s", Integer.toBinaryString(mValue & 0xFF)).replace(' ', '0'));
            sb.append('}');
            return sb.toString();
        }
    }

    private I2cDevice mDevice;
    private IODir mIODir = new IODir();
    private IPol mIPol = new IPol();
    private GPINTEN mGpIntEn = new GPINTEN();
    private DEFVAL mDefVal = new DEFVAL();
    private INTCON mIntCon = new INTCON();
    private IOCON mIoCon = new IOCON();
    private GPPU mGpPu = new GPPU();
    private OLAT mOLat = new OLAT();

    // This is 0b0100000 with the last three bits representing the state of the three address pins.
    private static final int I2C_ADDRESS = 0x20;
    private static final int GPIO_REG = 0x09;

    // Set all configuration options before opening device.
    public void open() throws IOException {
        mDevice = PeripheralManager.getInstance().openI2cDevice("I2C1", I2C_ADDRESS);
        mDevice.writeRegByte(IODir.REG, mIODir.getByte());
        mDevice.writeRegByte(IPol.REG, mIPol.getByte());
        mDevice.writeRegByte(GPINTEN.REG, mGpIntEn.getByte());
        mDevice.writeRegByte(DEFVAL.REG, mDefVal.getByte());
        mDevice.writeRegByte(INTCON.REG, mIntCon.getByte());
        mDevice.writeRegByte(GPPU.REG, mGpPu.getByte());
        mDevice.writeRegByte(OLAT.REG, mOLat.getByte());
    }

    public Values readGpio() throws IOException {
        return new Values(mDevice.readRegByte(GPIO_REG));
    }

    public void setPinDirection(int pin, Direction direction) {
        mIODir.setIoPinDirection(pin, direction);
    }

    public void setPinPolarity(int pin, Polarity polarity) {
        mIPol.setIoPinPolarity(pin, polarity);
    }

    public void setPinInterruptOnChange(int pin, boolean interruptOnChange) {
        mGpIntEn.setInterruptOnChange(pin, interruptOnChange);
    }

    public void setPinDefaultValue(int pin, boolean defaultValue) {
        mDefVal.setDefaultValue(pin, defaultValue);
    }

    public void setPinInterruptCompareTarget(int pin, CompareTarget target) {
        mIntCon.setCompareTarget(pin, target);
    }

    public void setPinPullupEnabled(int pin, boolean pullupEnabled) {
        mGpPu.enablePullup(pin, pullupEnabled);
    }

    public void setPinOutput(int pin, boolean value) {
        mOLat.setPin(pin, value);
    }

    public void setSequentialOperationEnabled(boolean enabled) {
        mIoCon.enableSequentialOperation(enabled);
    }

    public void setInterruptPinMode(InterruptPinMode mode) {
        mIoCon.setInterruptPinMode(mode);
    }

    public void setInterruptPinPolarity(InterruptPinPolarity polarity) {
        mIoCon.setInterruptPinPolarity(polarity);
    }

    public void setSlewRateControlEnabled(boolean enabled) {
        mIoCon.enableSlewRateControl(enabled);
    }

    private static short makeShort(int high_byte, int low_byte) {
        return (short)(((high_byte << 8) | (low_byte & 0xFF)) & 0xFFFF);
    }

    private static byte bit(int bit) {
        return (byte)(1 << bit);
    }

    private static byte setBitEnabled(byte value, int bit, boolean enabled) {
        if (enabled) {
            return (byte)(value | bit(bit));
        } else {
            return (byte)(value & ~bit(bit));
        }
    }

    // Direction of the IO pins.
    private static class IODir {
        public static final int REG = 0x00;
        private byte mValue = (byte)0xFF;

        public void setIoPinDirection(int pin, Direction direction) {
            mValue = setBitEnabled(mValue, pin, direction == Direction.INPUT);
        }

        public byte getByte() {
            return mValue;
        }
    }

    // Polarity of the input pins.
    private static class IPol {
        public static final int REG = 0x01;
        private byte mValue = 0;
        public void setIoPinPolarity(int pin, Polarity direction) {
            mValue = setBitEnabled(mValue, pin, direction == Polarity.NEGATED);
        }

        public byte getByte() {
            return mValue;
        }
    }

    // Enable pins to consider for interrupt on change.
    private static class GPINTEN {
        public static final int REG = 0x02;
        private byte mValue = 0;
        public void setInterruptOnChange(int pin, boolean interruptOnChange) {
            mValue = setBitEnabled(mValue, pin, interruptOnChange);
        }

        public byte getByte() {
            return mValue;
        }
    }

    // When using interrupt on change this sets the default value to compare against.
    private static class DEFVAL {
        public static final int REG = 0x03;
        private byte mValue = 0;
        public void setDefaultValue(int pin, boolean defaultValue) {
            mValue = setBitEnabled(mValue, pin, defaultValue);
        }

        public byte getByte() {
            return mValue;
        }
    }

    // When using interrupt on change this sets what the new value is compared against.
    private static class INTCON {
        public static final int REG = 0x04;

        private byte mValue = 0;
        public void setCompareTarget(int pin, CompareTarget compareTarget) {
            mValue = setBitEnabled(mValue, pin, compareTarget == CompareTarget.DEFVAL);
        }

        public byte getByte() {
            return mValue;
        }
    }

    // Configuration for IO expander
    private static class IOCON {
        public static final int REG = 0x05;

        private byte mValue = 0;

        public void enableSequentialOperation(boolean enabled) {
            mValue = setBitEnabled(mValue, 5, enabled);
        }

        public void enableSlewRateControl(boolean enabled) {
            mValue = setBitEnabled(mValue, 4, enabled);
        }

        public void setInterruptPinMode(InterruptPinMode mode) {
            mValue = setBitEnabled(mValue, 2, mode == InterruptPinMode.OPEN_DRAIN);
        }

        public void setInterruptPinPolarity(InterruptPinPolarity polarity) {
            mValue = setBitEnabled(mValue, 1, polarity == InterruptPinPolarity.ACTIVE_HIGH);
        }

        public byte getByte() {
            return mValue;
        }
    }

    // Pull-up resistor register
    private static class GPPU {
        public static final int REG = 0x06;

        private byte mValue = 0;
        public void enablePullup(int pin, boolean enabled) {
            mValue = setBitEnabled(mValue, pin, enabled);
        }

        public byte getByte() {
            return mValue;
        }
    }

    // Output Latch register
    private static class OLAT {
        public static final int REG = 0x0A;

        private byte mValue = 0;
        public void setPin(int pin, boolean enabled) {
            mValue = setBitEnabled(mValue, pin, enabled);
        }

        public byte getByte() {
            //  Log.d("MCP", String.format("OLAT: %02X", mValue));
            return mValue;
        }
    }

}
