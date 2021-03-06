/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ogema.channels;

import org.ogema.core.application.AppID;
import org.ogema.core.channelmanager.driverspi.DeviceLocator;
import org.ogema.core.channelmanager.driverspi.DeviceScanListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to remember running device scans.
 * Needed to be able to abort running scans.
 *
 * @author pau
 *
 */
class DeviceScanner implements DeviceScanListener {
    private static final Logger logger = LoggerFactory.getLogger(DeviceScanner.class);
    private final AppID appID;
    private final String interfaceId;
    private final String filter;
    private DeviceScanListener listener;
    private DeviceScanList parent;
    private boolean abort;

    DeviceScanner(String interfaceId, String filter, AppID appID, DeviceScanList parent, DeviceScanListener listener) {
        this.parent = parent;
        this.interfaceId = interfaceId;
        this.filter = filter;
        this.listener = listener;
        this.appID = appID;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((appID == null) ? 0 : appID.hashCode());
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        result = prime * result + ((interfaceId == null) ? 0 : interfaceId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DeviceScanner other = (DeviceScanner) obj;
        if (appID == null) {
            if (other.appID != null) {
                return false;
            }
        } else if (!appID.equals(other.appID)) {
            return false;
        }
        if (filter == null) {
            if (other.filter != null) {
                return false;
            }
        } else if (!filter.equals(other.filter)) {
            return false;
        }
        if (interfaceId == null) {
            if (other.interfaceId != null) {
                return false;
            }
        } else if (!interfaceId.equals(other.interfaceId)) {
            return false;
        }
        return true;
    }

    @Override
    public void progress(float ratio) {
        if (!abort) {
            try {
                if (listener != null) {
                    listener.progress(ratio);
                }
            } catch (Throwable t) {
                logger.warn("caught application exception in DeviceScanListener callback", t);
            }
        }
    }

    @Override
    public void finished(boolean success, Exception e) {
        if (!abort) {
            try {
                if (listener != null) {
                    listener.finished(success, e);
                }
            } catch (Throwable t) {
                logger.warn("caught application exception in DeviceScanListener callback", t);
            }
            parent.deviceScanFinished(this);
            // wake up waiting thread if finished correctly
            abort();
        }
    }

    @Override
    public void deviceFound(DeviceLocator device) {
        if (!abort) {
            try {
                if (listener != null) {
                    listener.deviceFound(device);
                }
            } catch (Throwable t) {
                logger.warn("caught application exception in DeviceScanListener callback", t);
            }
        }
    }

    synchronized void waitUntilFinished() {
        while (!abort) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }
    }

    synchronized void abort() {
        abort = true;
        this.notify();
        // clear references
        parent = null;
        listener = null;
    }

    AppID getAppID() {
        return appID;
    }

    String getInterfaceId() {
        return interfaceId;
    }

    String getFilter() {
        return filter;
    }
    
}
