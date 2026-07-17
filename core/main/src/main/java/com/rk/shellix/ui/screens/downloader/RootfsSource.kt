package com.rk.shellix.ui.screens.downloader

/**
 * Source definitions for downloading the Ubuntu Noble (24.04) base rootfs
 * tarball from Canonical, with a fallback URL path if the primary location
 * is unavailable.
 */
object RootfsSource {
    const val PRIMARY_BASE = "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/"

    fun tarballNameFor(arch: String): String {
        require(arch in setOf("arm64", "armhf", "amd64")) { "Unsupported arch: $arch" }
        return "ubuntu-base-24.04.4-base-$arch.tar.gz"
    }

    fun urlFor(arch: String): String = PRIMARY_BASE + tarballNameFor(arch)

    fun sha256UrlFor(arch: String): String = PRIMARY_BASE + "SHA256SUMS"

    fun fallbackUrlFor(arch: String): String =
        "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/" + tarballNameFor(arch)
}
