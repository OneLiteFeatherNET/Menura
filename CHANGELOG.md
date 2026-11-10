# Changelog

## [0.3.0](https://github.com/OneLiteFeatherNET/Menura/compare/v0.2.1...v0.3.0) (2026-11-10)


### Features

* **analytics:** collect metrics of external posts ([#49](https://github.com/OneLiteFeatherNET/Menura/issues/49)) ([071bcb1](https://github.com/OneLiteFeatherNET/Menura/commit/071bcb113d62676dd046c4dcdb57588ad84eacdb))
* **analytics:** serve dashboard aggregates from daily and weekly rollups ([#71](https://github.com/OneLiteFeatherNET/Menura/issues/71)) ([3206fca](https://github.com/OneLiteFeatherNET/Menura/commit/3206fca816a26694422748677274028a5a175821))
* **attribution:** attribute player joins to posts and series ([#53](https://github.com/OneLiteFeatherNET/Menura/issues/53)) ([dc482b5](https://github.com/OneLiteFeatherNET/Menura/commit/dc482b569c6db6b4d2e319037c15e93517a4e613))
* **brand:** add brand kit and layout templates ([#41](https://github.com/OneLiteFeatherNET/Menura/issues/41)) ([3a09748](https://github.com/OneLiteFeatherNET/Menura/commit/3a097486975a288372af23ce3c7c87b7ba882517))
* **brand:** bind the short's join domain to the installation join domain ([#64](https://github.com/OneLiteFeatherNET/Menura/issues/64)) ([2ce948d](https://github.com/OneLiteFeatherNET/Menura/commit/2ce948df6782b46bc8f8ffea37e4c3ac54194ceb))
* **library:** add asset upload ([#39](https://github.com/OneLiteFeatherNET/Menura/issues/39)) ([ebffe2d](https://github.com/OneLiteFeatherNET/Menura/commit/ebffe2df6bf06cfdb233b576f67eab512c6f123c))
* **library:** add the pool of clips, raw material and transcripts ([#43](https://github.com/OneLiteFeatherNET/Menura/issues/43)) ([d65f262](https://github.com/OneLiteFeatherNET/Menura/commit/d65f2627c8cd72dc6341110965e07c5d171d2c05))
* **library:** send chapter marks to youtube with the upload ([#61](https://github.com/OneLiteFeatherNET/Menura/issues/61)) ([6a979a8](https://github.com/OneLiteFeatherNET/Menura/commit/6a979a8a5a891159fc9d7b1a854de7ba2ed4ca7e))
* **library:** show where pool items were published and check layout templates ([#69](https://github.com/OneLiteFeatherNET/Menura/issues/69)) ([2de6f6e](https://github.com/OneLiteFeatherNET/Menura/commit/2de6f6efc517fbd4a0ad0192000223a473518fe8))
* **library:** upload raw material, finished assets and transcripts from the web ([#47](https://github.com/OneLiteFeatherNET/Menura/issues/47)) ([5a7b9d8](https://github.com/OneLiteFeatherNET/Menura/commit/5a7b9d8fe1a4d622b5832f17e9f737145136f8fb))
* **publish:** add calendar views and post planning in the web ([#57](https://github.com/OneLiteFeatherNET/Menura/issues/57)) ([546448a](https://github.com/OneLiteFeatherNET/Menura/commit/546448a8a3d98cd8bd9b477f71c1410762aa4c3d))
* **publish:** add channels page with consent connect and audit status ([#44](https://github.com/OneLiteFeatherNET/Menura/issues/44)) ([3247d4a](https://github.com/OneLiteFeatherNET/Menura/commit/3247d4ae99a4b0bd5f7664426336a60c90ae7859))
* **publish:** connect platform channels through consent ([#40](https://github.com/OneLiteFeatherNET/Menura/issues/40)) ([503a002](https://github.com/OneLiteFeatherNET/Menura/commit/503a002b3078ac037e388c89a7bde9352dca6b52))
* **publish:** import existing posts of connected channels ([#42](https://github.com/OneLiteFeatherNET/Menura/issues/42)) ([90b47d0](https://github.com/OneLiteFeatherNET/Menura/commit/90b47d075cc337e9b768ef17fadca5ffc9c3a331))
* **publish:** plan, approve, schedule and publish posts ([#51](https://github.com/OneLiteFeatherNET/Menura/issues/51)) ([26c599e](https://github.com/OneLiteFeatherNET/Menura/commit/26c599e247130a8d9902b62efa1858918c02747a))
* **series:** add content series ([#48](https://github.com/OneLiteFeatherNET/Menura/issues/48)) ([7d13824](https://github.com/OneLiteFeatherNET/Menura/commit/7d1382401b2342f5eeb6b8e627fafdf38a01f9dc))
* **web:** add role-aware navigation and routes ([#46](https://github.com/OneLiteFeatherNET/Menura/issues/46)) ([55d0211](https://github.com/OneLiteFeatherNET/Menura/commit/55d02115e22cc889447cd16b4c9c69e909e68376))
* **youtube:** import videos and read statistics from youtube ([#50](https://github.com/OneLiteFeatherNET/Menura/issues/50)) ([cdb7dd5](https://github.com/OneLiteFeatherNET/Menura/commit/cdb7dd5045d10f3e748c49ad3ceee276a5dbe9e4))
* **youtube:** publish videos and shorts to youtube natively ([#54](https://github.com/OneLiteFeatherNET/Menura/issues/54)) ([33b0b6e](https://github.com/OneLiteFeatherNET/Menura/commit/33b0b6e3cdb04895b3b1568a43d64f186c49a74d))
* **youtube:** refuse foreign playlists and warn about thumbnails ([#68](https://github.com/OneLiteFeatherNET/Menura/issues/68)) ([6444652](https://github.com/OneLiteFeatherNET/Menura/commit/6444652c40fb508af2fb300596e83fbace019c8b))
* **youtube:** simulate youtube locally until channels can be linked ([#73](https://github.com/OneLiteFeatherNET/Menura/issues/73)) ([86152ff](https://github.com/OneLiteFeatherNET/Menura/commit/86152ffec80646477477e4aa9d84d3140213cc16))


### Bug Fixes

* **attribution:** answer malformed join bodies without echoing them ([#63](https://github.com/OneLiteFeatherNET/Menura/issues/63)) ([56c9b29](https://github.com/OneLiteFeatherNET/Menura/commit/56c9b29cb565415c2eea3b560103d618e26ca951))
* **library:** give the retention job the object storage credentials ([#59](https://github.com/OneLiteFeatherNET/Menura/issues/59)) ([9ba6de9](https://github.com/OneLiteFeatherNET/Menura/commit/9ba6de95afc12daa428e849ddcc946a0bf047354))
* **publish:** always send null and empty channel response members ([#45](https://github.com/OneLiteFeatherNET/Menura/issues/45)) ([0db32c2](https://github.com/OneLiteFeatherNET/Menura/commit/0db32c26a941b32dbf177004deaec8c15cf1b12d))
* **server:** answer cors preflight of delete, put and patch requests ([#52](https://github.com/OneLiteFeatherNET/Menura/issues/52)) ([62d9adf](https://github.com/OneLiteFeatherNET/Menura/commit/62d9adfb0798206bc3bc46f8abc45ae71f404dd6))
* **server:** bind the body of a post patch ([#56](https://github.com/OneLiteFeatherNET/Menura/issues/56)) ([eefae31](https://github.com/OneLiteFeatherNET/Menura/commit/eefae31742945f3fa36466a52b19713e8fb6342f))
* **server:** send the empty tag lists of pool items ([#55](https://github.com/OneLiteFeatherNET/Menura/issues/55)) ([e10bf4a](https://github.com/OneLiteFeatherNET/Menura/commit/e10bf4aaa622c8569f020990e7c64f91b17bb70f))
* **youtube:** give the publication job the object storage credentials ([#58](https://github.com/OneLiteFeatherNET/Menura/issues/58)) ([4bbfc38](https://github.com/OneLiteFeatherNET/Menura/commit/4bbfc38046d81de68c9694251d9304d8514b3e4f))


### Documentation

* **brand:** list the parameters of every template format in the contract ([#65](https://github.com/OneLiteFeatherNET/Menura/issues/65)) ([6b4a37e](https://github.com/OneLiteFeatherNET/Menura/commit/6b4a37e40e602c87fea14a46012a2982b303c60b))
* **openspec:** keep first snapshots and seed the brand kit without a mark ([#72](https://github.com/OneLiteFeatherNET/Menura/issues/72)) ([442f068](https://github.com/OneLiteFeatherNET/Menura/commit/442f0688b02581bd282b12c69c2f8fea0c397073))
* **openspec:** propose add-asset-upload ([#26](https://github.com/OneLiteFeatherNET/Menura/issues/26)) ([41751db](https://github.com/OneLiteFeatherNET/Menura/commit/41751dbe5d03ef0562d6e70ee2fe33942486545e))
* **openspec:** propose add-brand-kit and add-brand-profiles ([#2](https://github.com/OneLiteFeatherNET/Menura/issues/2)) ([221fe87](https://github.com/OneLiteFeatherNET/Menura/commit/221fe8722d1d59ad365d5f4d54ca8d3fdaefd533))
* **openspec:** propose add-channel-connections ([#4](https://github.com/OneLiteFeatherNET/Menura/issues/4)) ([ce652c1](https://github.com/OneLiteFeatherNET/Menura/commit/ce652c1512c035439b44b4cdf32f2be8fb00d3d3))
* **openspec:** propose add-content-analytics ([#14](https://github.com/OneLiteFeatherNET/Menura/issues/14)) ([5af46eb](https://github.com/OneLiteFeatherNET/Menura/commit/5af46eb12ca3dedbc5d40fbec188f2055e2d50cc))
* **openspec:** propose add-content-distribution ([#9](https://github.com/OneLiteFeatherNET/Menura/issues/9)) ([065f8ab](https://github.com/OneLiteFeatherNET/Menura/commit/065f8ab1e9fb2219253dad42cbc271b6e58d0a3f))
* **openspec:** propose add-content-library ([#6](https://github.com/OneLiteFeatherNET/Menura/issues/6)) ([01a53fb](https://github.com/OneLiteFeatherNET/Menura/commit/01a53fb512009e7560b29f948b84720b4c69dd69))
* **openspec:** propose add-external-post-import ([#21](https://github.com/OneLiteFeatherNET/Menura/issues/21)) ([9bec6a5](https://github.com/OneLiteFeatherNET/Menura/commit/9bec6a5ea77f83a90ad15769a7266a84e036b776))
* **openspec:** propose add-ui-workbench ([#36](https://github.com/OneLiteFeatherNET/Menura/issues/36)) ([e6c23ce](https://github.com/OneLiteFeatherNET/Menura/commit/e6c23ceef86d87e941cb88d81f7919d97103aa20))
* **openspec:** propose add-youtube-insights ([#22](https://github.com/OneLiteFeatherNET/Menura/issues/22)) ([c234cf4](https://github.com/OneLiteFeatherNET/Menura/commit/c234cf41a6e9deb3ac4e92a29bff9919262458af))
* **openspec:** propose add-youtube-integration ([#15](https://github.com/OneLiteFeatherNET/Menura/issues/15)) ([16df760](https://github.com/OneLiteFeatherNET/Menura/commit/16df760b8edc91cc400a50727b9cc4e2ebeff82a))
* **openspec:** propose content series and join attribution ([#35](https://github.com/OneLiteFeatherNET/Menura/issues/35)) ([004bce2](https://github.com/OneLiteFeatherNET/Menura/commit/004bce2e892c10e0045827b2c0da05e3fc166986))
* **publish:** describe the lineage of the sourcePostId of posts and calendar items ([#67](https://github.com/OneLiteFeatherNET/Menura/issues/67)) ([aaa1d44](https://github.com/OneLiteFeatherNET/Menura/commit/aaa1d44b50b11ab8ed3ea33fb1b7d2228571c6ca))

## [0.2.1](https://github.com/OneLiteFeatherNET/Menura/compare/v0.2.0...v0.2.1) (2026-11-03)


### Bug Fixes

* **jobs:** make the jobs launcher executable in the image ([#32](https://github.com/OneLiteFeatherNET/Menura/issues/32)) ([0fe5d8e](https://github.com/OneLiteFeatherNET/Menura/commit/0fe5d8ef8366fc610930534573ca43fb445cbf85))

## [0.2.0](https://github.com/OneLiteFeatherNET/Menura/compare/v0.1.0...v0.2.0) (2026-11-02)


### Features

* **auth:** add access control ([#30](https://github.com/OneLiteFeatherNET/Menura/issues/30)) ([b7d5db1](https://github.com/OneLiteFeatherNET/Menura/commit/b7d5db125330fb0b89b944bf6241131f90ae8626))


### Documentation

* add readme and license ([9fb4529](https://github.com/OneLiteFeatherNET/Menura/commit/9fb45296039cefd616364febfcbb928adbfb025b))
* **openspec:** propose add-access-control ([#1](https://github.com/OneLiteFeatherNET/Menura/issues/1)) ([2b3c14f](https://github.com/OneLiteFeatherNET/Menura/commit/2b3c14fb90c8e50d1a95308d0c8b586b03cd0684))
* **openspec:** propose add-ci-pipeline ([#20](https://github.com/OneLiteFeatherNET/Menura/issues/20)) ([2890637](https://github.com/OneLiteFeatherNET/Menura/commit/2890637b93dbfb70b5d7b6290571387e4b2c3a94))
* **openspec:** propose add-project-skeleton ([#19](https://github.com/OneLiteFeatherNET/Menura/issues/19)) ([9d225dc](https://github.com/OneLiteFeatherNET/Menura/commit/9d225dc7fb61021d77d8eeaf30f893686f7dc999))
