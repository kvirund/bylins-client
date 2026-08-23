package com.bylins.client

enum class OperatingSystem {
    MacOS,
    Windows,
    Linux,
    Other;

    companion object {
        val current: OperatingSystem by lazy {
            from(System.getProperty("os.name"))
        }

        fun from(osName: String?): OperatingSystem {
            val name = osName.orEmpty().lowercase()
            return when {
                "mac" in name -> MacOS
                "win" in name -> Windows
                "nux" in name || "nix" in name -> Linux
                else -> Other
            }
        }
    }
}
