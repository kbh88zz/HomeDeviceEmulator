/*
 * Copyright (C) 2023 Korea Association of AI Smart Home.
 * Copyright (C) 2023 KyungDong Navien Co, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kr.or.kashi.hde.ksx4506;

import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.GasValve;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * [KS X 4506] The gas-valve implementation for GasValve
 */
public class KSGasValve extends KSDeviceContextBase {
    private static final String TAG = "KSGasValve";
    private static final boolean DBG = true;

    public KSGasValve(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, GasValve.class);

        // Register the tasks to be performed when specific property changes.
        setPropertyTask(GasValve.PROP_CURRENT_STATES, mSingleControlTask);
        setPropertyTask(GasValve.PROP_CURRENT_ALARMS, mSingleControlTask);
        setPropertyTask(HomeDevice.PROP_ONOFF, mSingleControlTask);
    }

    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        parseGasValveStateByte(packet.data[1], outProps);

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-chr-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final int data1 = packet.data[1] & 0xFF;

        long supportedStates = 0L;
        long supportedAlarms = 0L;

        supportedStates |= GasValve.State.GAS_VALVE; // Gas valve is default as supported in standard specification.
        if ((data1 & (1L << 0)) != 0L) supportedAlarms |= (GasValve.Alarm.EXTINGUISHER_BUZZING);
        if ((data1 & (1L << 1)) != 0L) supportedAlarms |= (GasValve.Alarm.GAS_LEAKAGE_DETECTED);

        outProps.put(GasValve.PROP_SUPPORTED_STATES, supportedStates);
        outProps.put(GasValve.PROP_SUPPORTED_ALARMS, supportedAlarms);

        return PARSE_OK_PEER_DETECTED;
    }

    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-status-ctrl-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-single-ctrl-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        parseGasValveStateByte(packet.data[1], outProps);

        return PARSE_OK_ACTION_PERFORMED;
    }

    protected void parseGasValveStateByte(byte stateByte, PropertyMap outProps) {
        long newStates = 0;
        long newAlarms = 0;

        final boolean gasValveOpened    = ((stateByte & (1L << 0)) != 0L);
        final boolean gasValveClosing   = ((stateByte & (1L << 1)) != 0L);
        final boolean gasValveChanging  = ((stateByte & (1L << 2)) != 0L);

        if (gasValveOpened || gasValveChanging) {
            // Treat opened or changing state as opened.
            newStates |= GasValve.State.GAS_VALVE;
        }

        if ((stateByte & (1L << 3)) != 0L) {    // fire-extinguisher buzzer on
            newAlarms |= GasValve.Alarm.EXTINGUISHER_BUZZING;
        }

        if ((stateByte & (1L << 4)) != 0L) {    // gas leakage detected
            newAlarms |= GasValve.Alarm.GAS_LEAKAGE_DETECTED;
        }

        outProps.put(HomeDevice.PROP_ONOFF, ((newStates & GasValve.State.GAS_VALVE) != 0L));
        outProps.put(GasValve.PROP_CURRENT_STATES, newStates);
        outProps.put(GasValve.PROP_CURRENT_ALARMS, newAlarms);
    }

    @Override
    protected KSPacket makeControlReq(PropertyMap props) {
        long curStates = (Long) getProperty(GasValve.PROP_CURRENT_STATES).getValue();
        long reqStates = props.get(GasValve.PROP_CURRENT_STATES, Long.class);
        long difStates = curStates ^ reqStates;

        if ((difStates & GasValve.State.GAS_VALVE) == 0) {
            // Check also changes of on/off state, and overide switch state if so.
            final boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
            final boolean reqOnOff = (Boolean) props.get(HomeDevice.PROP_ONOFF).getValue();
            if (reqOnOff != curOnOff) {
                if (reqOnOff) reqStates |= GasValve.State.GAS_VALVE;
                else reqStates &= ~GasValve.State.GAS_VALVE;
                difStates = curStates ^ reqStates; // Recalulate diff of current and new.
            }
        }

        long reqAlarms = props.get(GasValve.PROP_CURRENT_ALARMS, Long.class);

        final byte controlData = makeControlReqData(reqStates, reqAlarms);
        return createPacket(CMD_SINGLE_CONTROL_REQ, controlData);
    }

    protected byte makeControlReqData(long states, long alarms) {
        byte controlData = 0;

        if ((states & GasValve.State.GAS_VALVE) == 0L) {
            controlData |= (byte)(1 << 0);  // 1:close valve
        }

        if ((alarms & GasValve.Alarm.EXTINGUISHER_BUZZING) == 0L) {
            controlData |= (byte)(1 << 1);  // 1:stop buzzer
        }

        return controlData;
    }
}
