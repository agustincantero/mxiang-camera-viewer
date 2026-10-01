//go:build windows

package lan

import (
	"encoding/binary"
	"fmt"
	"net"
	"syscall"
	"unsafe"
)

var (
	iphlpapi    = syscall.NewLazyDLL("iphlpapi.dll")
	procSendARP = iphlpapi.NewProc("SendARP")
)

// hardwareAddr resolves the MAC of a host on a directly attached subnet. The
// device has just sent us a datagram, so the entry is in the neighbour table
// and this does not put a packet on the wire.
func hardwareAddr(ip net.IP) (net.HardwareAddr, error) {
	ip4 := ip.To4()
	if ip4 == nil {
		return nil, fmt.Errorf("lan: %s is not IPv4", ip)
	}

	// SendARP takes the address as a ULONG already in network byte order, which
	// is what reading the octets little-endian on a little-endian host gives.
	dest := binary.LittleEndian.Uint32(ip4)

	var mac [8]byte
	length := uint32(len(mac))

	r, _, _ := procSendARP.Call(
		uintptr(dest), 0,
		uintptr(unsafe.Pointer(&mac[0])),
		uintptr(unsafe.Pointer(&length)))

	if r != 0 {
		return nil, fmt.Errorf("lan: SendARP for %s failed with code %d", ip, r)
	}
	if length == 0 || length > uint32(len(mac)) {
		return nil, fmt.Errorf("lan: SendARP for %s returned %d bytes", ip, length)
	}

	return net.HardwareAddr(mac[:length]), nil
}
