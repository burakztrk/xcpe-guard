# AdGuard blocklist for the XCPE botnet

[Türkçe](#türkçe)

The app switches the backdoor off on the box, but the botnet's servers can still be reached by every other device on
your network. This list blocks the C2, proxy-SDK and file-hosting names that Google Threat Intelligence published in
[Shutting the door on the XCPE botnet](https://cloud.google.com/blog/topics/threat-intelligence/shutting-the-door-on-the-xcpe-botnet)
at the DNS level, so the box (and anything else) cannot resolve them. It has 154 rules: 150 domains and 4 IP addresses.

## Use it

**Subscribe (updates when this file changes).** In AdGuard Home open **Filters → DNS blocklists → Add blocklist →
Add a custom list** and paste:

```
https://raw.githubusercontent.com/burakztrk/xcpe-guard/main/docs/adguard-xcpe.txt
```

**Or copy the rules.** Open **Filters → Custom filtering rules** and paste the list below. Any other DNS filter that
understands AdGuard syntax can use the same rules.

Then open XCPE Guard and press **Scan now**. The "C2 names (DNS)" row turns green when none of the names resolve.

## Good to know

- Some names belong to "share your internet" services (`earn.fm`, `traffmonetizer.com`) and to proxy SDKs the botnet
  installs. Blocking them is the point; if you knowingly use one of those services, remove its line.
- The four IP rules only match lookups of the literal address, so they rarely do anything in DNS. Blocking the
  addresses themselves needs a firewall rule on your router.
- If the box still resolves the names after you add the list, check **Private DNS**: with "Automatic" Android can send
  every lookup encrypted to a public resolver such as 1.1.1.1 and skip your AdGuard. Switch it off, or set the router's
  DHCP to hand out only your AdGuard address. XCPE Guard shows this under *Private DNS*.

## The list

```
! XCPE botnet IOC - Google GTIG (2026) - AdGuard rules
! Source: https://cloud.google.com/blog/topics/threat-intelligence/shutting-the-door-on-the-xcpe-botnet
||amhib.com^
||disview.com^
||prataw.com^
||auqee.com^
||alptk.com^
||mfiny.com^
||leisurepuz.com^
||earn.fm^
||geo-loadbalanced.com^
||shixunsuda.com^
||gtxvdqvuweqs.com^
||hiddeninplainsight.top^
||absolut.fit^
||alpivara.com^
||beepityping.com^
||beezlepongmad.com^
||bloopondle.com^
||blustpong.com^
||braigwshat.com^
||brisping.com^
||bronglepong.com^
||clapslpist.com^
||clapslport.com^
||craipivolta.com^
||creapslpinut.com^
||crodipolis.com^
||delpiverno.com^
||depomancy.com^
||dewswyra.com^
||drackpingcare.com^
||dramapi.com^
||drapithon.com^
||fawsilver.com^
||fawsindra.com^
||fizzlepong.com^
||flaigwern.com^
||flapilo.com^
||flapslwstorm.com^
||flawsino.com^
||flewsnark.com^
||floporin.com^
||floppipple.com^
||flumpipoodle.com^
||frosping.com^
||gapslpinch.com^
||gapslwsbor.com^
||glapoton.com^
||glewsnap.com^
||glippinger.com^
||gloobfnatch.com^
||gloopong.com^
||glowspar.com^
||grasplpond.com^
||grinapslpocal.com^
||hawsith.com^
||lapslponze.com^
||lepovia.com^
||limpingcare.com^
||lorapslpifyx.com^
||lowswyn.com^
||lunapiris.com^
||mopith.com^
||mudponger.com^
||nepiral.com^
||opostra.com^
||pibberflop.com^
||pikaroodle.com^
||ploponix.com^
||qepoth.com^
||rapinel.com^
||rapslpine.com^
||rapslponic.com^
||rapslwsweave.com^
||raspongle.com^
||ripotus.com^
||rowswyn.com^
||scapslwshell.com^
||snarpong.com^
||snorpdoodle.com^
||sofpilan.com^
||soporth.com^
||spopel.com^
||tawserin.com^
||tawswizzle.com^
||teowsmera.com^
||tepiol.com^
||tewsmariel.com^
||thaiporix.com^
||thapolex.com^
||torpoodle.com^
||trapslwsin.com^
||trawsica.com^
||tripikazu.com^
||twindping.com^
||vaiporun.com^
||vespilara.com^
||vorpolis.com^
||whispong.com^
||winklenark.com^
||wizzleplonk.com^
||wogglenift.com^
||yapodust.com^
||yewsara.com^
||zarflonk.com^
||zenapslpinic.com^
||zerapslpomat.com^
||zimpire.com^
||zingpingbees.com^
||zippypongbee.com^
||zoraipinex.com^
||zorapslwsnic.com^
||zrawsomble.com^
||bestipip.com^
||fecebbbk.xyz^
||guangzhouroot.com^
||bd-devmetrics.net^
||tx-devmetrics.net^
||linkfog.cn^
||cloudbrokerapi.xyz^
||dddlink.link^
||smrtai.cc^
||bkdomain.cn^
||columbus2024.com^
||dmurdce.xyz^
||edisajalsjfdfh.shop^
||fshuaons2.online^
||fuioahsdoa3.store^
||fuisans1.online^
||haimnsgsjhansj.shop^
||ishano456.sbs^
||ishuoabh3years.online^
||sdoisanadf.shop^
||shaiosjdaln.shop^
||shsuanalsjai.shop^
||ty4523.space^
||ty54fgd435.my^
||ue886578433.online^
||vrr8345.site^
||xmsae.sbs^
||xshaon123.sbs^
||dgjm.online^
||drgdj.top^
||spiritlib.cyou^
||traffmonetizer.com^
||anyevonline.com^
||intermedia-arts.tv^
||szbboys.com^
||yokatvonline.com^
||219.134.163.210^
||219.134.163.211^
||23.94.49.57^
||38.145.197.135^
||jaguar-distributor.logreporter.com^
||jaguar-distributor.syslogcollector.com^
```

---

## Türkçe

Uygulama arka kapıyı kutuda kapatır, ama botnetin sunucularına ağındaki diğer her cihaz hâlâ ulaşabilir. Bu liste,
Google Threat Intelligence'ın
[XCPE raporunda](https://cloud.google.com/blog/topics/threat-intelligence/shutting-the-door-on-the-xcpe-botnet)
yayımladığı C2, proxy SDK ve dosya barındırma adlarını DNS seviyesinde engeller; kutu (ve ağdaki her şey) bunları
çözemez. Toplam 154 kural: 150 alan adı ve 4 IP adresi.

**Abone ol (dosya değişince güncellenir).** AdGuard Home'da **Filtreler → DNS engelleme listeleri → Engelleme listesi
ekle → Özel liste ekle** yolundan şu adresi yapıştır:

```
https://raw.githubusercontent.com/burakztrk/xcpe-guard/main/docs/adguard-xcpe.txt
```

**Ya da kuralları kopyala.** **Filtreler → Özel filtreleme kuralları** bölümüne yukarıdaki listeyi yapıştır. Sonra
XCPE Guard'ı açıp **Şimdi tara**'ya bas; hiçbir ad çözülmüyorsa "C2 alan adları (DNS)" satırı yeşil olur.

Bilmen gerekenler:
- Bazı adlar "internetini paylaş" hizmetlerine (`earn.fm`, `traffmonetizer.com`) ve botnetin kurduğu proxy SDK'larına
  ait. Engellemek amaç; bunlardan birini bilerek kullanıyorsan satırını sil.
- Dört IP kuralı DNS'te yalnızca adresin kendisinin sorgulanmasını yakalar, pratikte pek işe yaramaz. Adreslerin
  kendisini engellemek için router'da güvenlik duvarı kuralı gerekir.
- Liste eklenmesine rağmen kutu adları çözüyorsa **Özel DNS**'e bak: "Otomatik" iken Android sorguları şifreli olarak
  1.1.1.1 gibi genel bir sunucuya gönderip AdGuard'ı atlayabilir. Kapat ya da router'ın DHCP'sinde yalnızca AdGuard
  adresini dağıttır.
