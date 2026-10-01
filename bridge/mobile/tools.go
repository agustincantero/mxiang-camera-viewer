//go:build tools

package xmbridge

// Keeps golang.org/x/mobile/bind in go.mod, which `gomobile bind` needs to generate its glue code.
import _ "golang.org/x/mobile/bind"
