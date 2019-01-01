# iTXTech Daedalus

[![Donate](https://img.shields.io/badge/alipay-donate-yellow.svg)](https://qr.alipay.com/a6x07022gffiehykicipv1a)
[![Build Status](https://travis-ci.org/iTXTech/Daedalus.svg?branch=master)](https://travis-ci.org/iTXTech/Daedalus)
[![Jenkins](https://img.shields.io/jenkins/s/http/dev.itxtech.org:10298/job/Daedalus.svg)](http://dev.itxtech.org:10298/job/Daedalus/)

__No root required Android DNS modifier and Hosts/DNSMasq resolver.__

## Installation
* __[Jenkins](http://dev.itxtech.org:10298/job/Daedalus/)__ - Debug signature
* __[Releases](https://github.com/iTXTech/Daedalus/releases)__ - Release signature
* __[Play Test](https://play.google.com/apps/testing/org.itxtech.daedalus)__ - Release signature

[<img alt='Get it on Google Play'
      src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png'
      height="80">](https://play.google.com/store/apps/details?id=org.itxtech.daedalus)

## Useful links
* __[Telegram](https://t.me/iTXTechDaedalus)__
* __[Wiki](https://github.com/iTXTech/Daedalus/wiki)__ - __See before using DoH__

## Introduction

This application can create a VPN tunnel to modify the DNS settings on Android.<br>
Through the DNS server and rules which are provided by third parties, users can visit Google, Twitter and so on via https protocol directly without a VPN.<br>
<br>
Features:
* No root access required, no ads contained
* Functional under data connection
* A tester for DNS servers
* IPv6 support (including Rules!)
* Custom DNS server
* Custom hosts and DNSMasq configuration
* EXTREME LOW power consume
* Material Design

Supported DNS Query Methods:
* UDP
* TCP 
* DNS over TLS ([RFC7858](https://tools.ietf.org/html/rfc7858))
* DNS over HTTPS ([RFC8484](https://tools.ietf.org/html/rfc8484))
* DNS over HTTPS ([Google JSON](https://developers.google.com/speed/public-dns/docs/dns-over-https))
<br>

__Users must comply with local laws and regulations.__<br>

## DNS Server Providers

* __CuteDNS__ - *Shutdown according to regulations*
* __[FUN DNS](http://fundns.cn)__ - *Shutdown according to regulations*
* __[Pure DNS](https://puredns.cn/)__
* __[PdoMo-DNS](https://pdomo.me/)__ - *Intelligent Free Public DNS*
* __[rubyfish](https://www.rubyfish.cn)__ - *Free DoT/DoH DNS*

## Rule Providers

* __[Daedalus Default](https://github.com/iTXTech/Daedalus/blob/master/default.hosts)__ - __Default Rules for Built-in DoH servers__
* __[hosts](https://github.com/googlehosts/hosts)__ by *[googlehosts](https://github.com/googlehosts)* - [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh)
* __[yhosts](https://github.com/vokins/yhosts)__ by *[vokins](https://github.com/vokins)* - [CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/)

## Requirements

* Minimum Android version: 4.0.3 (API 15) - __*Basic VPN functions*__
* Recommended Android version: >= 5.0 (API 21) - __*Full features*__
* Best Android version: >= 7.1 (API 25) - __*Launcher shortcuts*__

## Open Source Licenses

* __[ClearEditText](https://github.com/MrFuFuFu/ClearEditText)__ by *[Yuan Fu](https://github.com/MrFuFuFu)* - [APL 2.0](https://github.com/MrFuFuFu/ClearEditText)
* __[DNS66](https://github.com/julian-klode/dns66)__ by *[Julian Andres Klode](https://github.com/julian-klode)* - [GPLv3](https://github.com/julian-klode/dns66/blob/master/COPYING)
* __[Pcap4J](https://github.com/kaitoy/pcap4j)__ by *[Kaito Yamada](https://github.com/kaitoy)* - [MIT](https://github.com/kaitoy/pcap4j)
* __[MiniDNS](https://github.com/rtreffer/minidns)__ by *[Rene Treffer](https://github.com/rtreffer)* - [LGPLv2.1](https://github.com/rtreffer/minidns/blob/master/LICENCE_LGPL2.1)
* __[Gson](https://github.com/google/gson)__ by *[Google](https://github.com/google)* - [APL 2.0](https://github.com/google/gson/blob/master/LICENSE)
* __[Shadowsocks](https://github.com/shadowsocks/shadowsocks-android)__ by *[Shadowsocks](https://github.com/shadowsocks)* - [GPLv3](https://github.com/shadowsocks/shadowsocks-android/blob/master/LICENSE)

## Credits

* __[JetBrains](https://www.jetbrains.com/)__ - For providing free license for [IntelliJ IDEA](https://www.jetbrains.com/idea/)
* __[ShenniaoTech](https://www.sncidc.com/)__ - For supporting us with love

## License

    Copyright (C) 2017-2019 iTX Technologies <admin@itxtech.org>
    
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
