//go:build !windows

package lan

import (
	"errors"
	"net"
)

// hardwareAddr cannot look a MAC up off Windows: Android, for one, no longer lets an app read the neighbour table. A camera is then found by the address the cloud reports and never by its MAC.
func hardwareAddr(net.IP) (net.HardwareAddr, error) {
	return nil, errors.New("lan: resolving a hardware address is not supported on this platform")
}
