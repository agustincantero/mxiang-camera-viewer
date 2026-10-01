// Package xmbridge is the gomobile surface over the Xiaomi protocol code in
// internal/, for the Android app: JSON in and out for the control plane, one
// handle per media session.
package xmbridge

import (
	"net"
	"strings"

	"github.com/agustincantero/mxiang-camera-viewer/bridge/internal/api"
	"github.com/agustincantero/mxiang-camera-viewer/bridge/internal/lan"
	"github.com/agustincantero/mxiang-camera-viewer/bridge/internal/stream"
)

// Call runs one control-plane request and returns its JSON response. Like the
// DLL entry point it never fails: errors travel inside the {"ok":false} envelope.
func Call(method string, request string) string {
	return string(api.HandleCall(method, []byte(request)))
}

// SetLocalNetworks gives the bridge the IPv4 networks the device is on, as
// comma-separated CIDR blocks such as "10.0.0.5/24". Android will not let the
// process list its own interfaces, and finding a camera by broadcast needs them.
func SetLocalNetworks(cidrs string) {
	var nets []*net.IPNet
	for _, block := range strings.Split(cidrs, ",") {
		ip, network, err := net.ParseCIDR(strings.TrimSpace(block))
		if err != nil || ip.To4() == nil {
			continue
		}
		nets = append(nets, &net.IPNet{IP: ip, Mask: network.Mask})
	}
	lan.SetLocalNets(nets)
}

// Stream is one media session with a camera.
type Stream struct {
	session  *stream.Session
	response string
}

// OpenStream connects to a camera. It never returns nil: check Opened, and read
// Response for the JSON describing either the connection or why it failed.
func OpenStream(request string) *Stream {
	session, response := api.OpenStream([]byte(request))
	return &Stream{session: session, response: string(response)}
}

// Opened reports whether the camera answered.
func (s *Stream) Opened() bool { return s.session != nil }

// Response is the JSON envelope OpenStream produced.
func (s *Stream) Response() string { return s.response }

// Read blocks until the next access unit arrives, and fails once the session ends.
func (s *Stream) Read() (*Frame, error) {
	frame, err := s.session.Read()
	if err != nil {
		return nil, err
	}
	return &Frame{frame: frame}, nil
}

// Command runs an in-band command such as {"method":"ptz.step","direction":"left"}.
func (s *Stream) Command(request string) string {
	return string(api.HandleStreamCommand(s.session, []byte(request)))
}

// Close ends the session and releases a Read that is blocked on it.
func (s *Stream) Close() {
	if s.session != nil {
		s.session.Close()
	}
}

// Frame is one access unit. Video payloads are Annex-B byte streams.
type Frame struct {
	frame *stream.Frame
}

// Kind is 1 for video and 2 for audio.
func (f *Frame) Kind() int { return int(f.frame.Kind) }

// Codec is 1 H.264, 2 H.265, 3 G.711 A-law, 4 Opus, 5 PCM and 6 G.711 mu-law.
func (f *Frame) Codec() int { return int(f.frame.Codec) }

// Keyframe is true for a video access unit that starts a GOP.
func (f *Frame) Keyframe() bool { return f.frame.Keyframe }

// SampleRate is the audio rate in Hz the camera declared, or 0.
func (f *Frame) SampleRate() int { return int(f.frame.SampleRate) }

// PtsMs is the presentation time in milliseconds.
func (f *Frame) PtsMs() int64 { return f.frame.PTS }

// Data is the payload.
func (f *Frame) Data() []byte { return f.frame.Data }
