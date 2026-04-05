'use strict';

const os = require('os');

// Fix os.cpus() on Android
const _origCpus = os.cpus;
os.cpus = function() {
    try { return _origCpus.call(os); }
    catch(e) { return [{ model: 'ARM64', speed: 0, times: { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } }]; }
};

// Fix os.networkInterfaces() on Android (Bionic libc issue)
const _origNif = os.networkInterfaces;
os.networkInterfaces = function() {
    try { return _origNif.call(os); }
    catch(e) { 
        return { 
            lo: [{ 
                address: '127.0.0.1', 
                netmask: '255.0.0.0', 
                family: 'IPv4', 
                mac: '00:00:00:00:00:00', 
                internal: true, 
                cidr: '127.0.0.1/8' 
            }] 
        }; 
    }
};

// Fix os.tmpdir() if needed
const _origTmpdir = os.tmpdir;
os.tmpdir = function() {
    try { return _origTmpdir.call(os); }
    catch(e) { return process.env.TMPDIR || '/tmp'; }
};
