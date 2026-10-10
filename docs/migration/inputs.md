# 构建与验证输入快照

这些文件主要位于两个 Git 项目之外。本次记录内容哈希；后续迁移需要将手工维护的验证代码收纳到目标仓库。

记录文件存在，不代表本次已经执行测试或确认新工程通过。具体迁移方式见 ../../MIGRATION.md。

| 工作区路径 | SHA-256 |
| --- | --- |
| [build-project/build-mod.ps1](../../../build-project/build-mod.ps1) | `187bbf5d3533fb5d2e0e303ccfb2de220f3acb9cd678b09c8acd37d17eb8e11f` |
| [build-project/build.gradle](../../../build-project/build.gradle) | `8fcba924b7cd7faab175522e0c287b5ee36c2f72cf9cb1140743dd2586839a39` |
| [build-project/BUILD.md](../../../build-project/BUILD.md) | `9fd5f3dc677e817e3805fc3397b59e5091d6592bb8eb2df3733e995f42a49b20` |
| [build-project/collect-local-dependencies.ps1](../../../build-project/collect-local-dependencies.ps1) | `43bd0e72a3a7aceaf1ad39896a42752520202ab32f92891905090f8b63381365` |
| [build-project/dependency-manifest.json](../../../build-project/dependency-manifest.json) | `21af8bdc7c2877e20807c35aa80a91c75f953e988ac31cffbe932131e9c2bbd9` |
| [build-project/extract-patch-methods.ps1](../../../build-project/extract-patch-methods.ps1) | `30f9bf6f997bc790be00d0cce28220ebfc422244e2e7360a14bb0afbc2bd373f` |
| [build-project/gradle.properties](../../../build-project/gradle.properties) | `5b5aa1d63c2d02c2a39f8a207bdc91a7fbcddbfb50e0e92c59da3ecd6a6478a6` |
| [build-project/package-installation.ps1](../../../build-project/package-installation.ps1) | `dbf2ed22f8f858c937237c7d6e3cedab7056a669f97ca8543ece5b0563dc2415` |
| [build-project/settings.gradle](../../../build-project/settings.gradle) | `21c8c1cfea9f78f7fed6d7ad325aafa24e0d1bd330a40719997c303d2217b830` |
| [build-project/tools/BuildRagdollJar.java](../../../build-project/tools/BuildRagdollJar.java) | `483c9662703240301a80e235f1cc38563e31b1f142cdf059c8927b0a9aa5d187` |
| [build-project/tools/RealPacketTest.java](../../../build-project/tools/RealPacketTest.java) | `567ca96e8bd14e7062295abb26154ee2e417d3a3152a8a5c978af359ddc36295` |
| [build-project/tools/VerifyRagdollJar.java](../../../build-project/tools/VerifyRagdollJar.java) | `cf2ad7c5bc5634b778ec899cf508b2a532734c08cbe9aa4f91d2e339b6181bd3` |
| [build-project/verify-mod.ps1](../../../build-project/verify-mod.ps1) | `6512e27a079a922d5dac9605a15dc88b6d0048fdc9ea2f3ddc6874cf9b373239` |
| [verification/tests/AmmoGrantTest.java](../../../verification/tests/AmmoGrantTest.java) | `473a8e6ee6e970453851f6bc6de7c60945efa159d127ca2ae5bf8bbb0c05277a` |
| [verification/tests/AmmoSupplySoundTest.java](../../../verification/tests/AmmoSupplySoundTest.java) | `63f5da0f413f33dd8eee39f7a48ca15103355a2f612949585f49c5bd2c8c9600` |
| [verification/tests/BoundaryDeathsTest.java](../../../verification/tests/BoundaryDeathsTest.java) | `6b4e0118aa5a3a38a3c96e36fabbfdaf39f33c66da855548993528bc69d927c2` |
| [verification/tests/BoundaryGpuTest.java](../../../verification/tests/BoundaryGpuTest.java) | `319956c0b777d47b699f2879adfb045bc5c9ee47840acd734e3b04090c3ac7fb` |
| [verification/tests/BoundaryScopeTest.java](../../../verification/tests/BoundaryScopeTest.java) | `700cb693d2904ed6608661bb218ac9eaffdaab5417f3fdc9d258b7218ddb24cc` |
| [verification/tests/BoundaryTest.java](../../../verification/tests/BoundaryTest.java) | `2f8d3a1605fa21a17032e3e04ae2647e25f93cbd458c47e8fba56d77384201d8` |
| [verification/tests/ClientVisibilitySubscriptionTest.java](../../../verification/tests/ClientVisibilitySubscriptionTest.java) | `6477bb6af0ffd04b4c99571c48abca500018be250d60b1f55c7a2df2a2ae0074` |
| [verification/tests/DeathCauseScopeTest.java](../../../verification/tests/DeathCauseScopeTest.java) | `0f6f9527ace2dfa809dc5b8b7383bdf2ab6957117a3b78fd273f50ced4b45549` |
| [verification/tests/DeathCauseTest.java](../../../verification/tests/DeathCauseTest.java) | `23fafb66f078fe40f25087d98413b9192b4a1e7a52e3c141e67ae6ffb0631fdf` |
| [verification/tests/DeathDispatchTest.java](../../../verification/tests/DeathDispatchTest.java) | `fb6325ac35054068092a447bde4451164f42a6be4c62c3c4417609e4c714f94d` |
| [verification/tests/DeathFilterGpuTest.java](../../../verification/tests/DeathFilterGpuTest.java) | `9b84eed0d8de1978e4b0dc720e35409767b8e968d83c72b0af2bb9c8178c1808` |
| [verification/tests/DeathFilterScopeTest.java](../../../verification/tests/DeathFilterScopeTest.java) | `565235caff98998d66b13f1262b79ccb548f1882db40277bbf092b72a582c133` |
| [verification/tests/DeathFilterTransitionTest.java](../../../verification/tests/DeathFilterTransitionTest.java) | `3640b280998a2687d0084520eb144dfe2a3969e953858d9cf55e42a4b964c7bb` |
| [verification/tests/DownedViewTest.java](../../../verification/tests/DownedViewTest.java) | `25b4e9ce4ba75217201f03861f1b5f548f33ce9941de7d02c167b40b742e1d21` |
| [verification/tests/IsolationMixinService.java](../../../verification/tests/IsolationMixinService.java) | `2332a0d56697ae1aeb870fff09365ec41aa2ffdb6e103f6609edb38e37e93745` |
| [verification/tests/IsolationProperties.java](../../../verification/tests/IsolationProperties.java) | `18d69a26eb418e7adb889e3e63e4dc1a55f6da7241339a19a03936cdfb89c4aa` |
| [verification/tests/KillerCardBehaviorTest.java](../../../verification/tests/KillerCardBehaviorTest.java) | `a76b8d99636c19206fd4721ac497f055f0902dfbe7e08a417f4020f309181125` |
| [verification/tests/KillerCardScopeTest.java](../../../verification/tests/KillerCardScopeTest.java) | `d8f1e4614e0263acd79bbfd52d09e6496b1e6b602d88fa5e1bae3933af0aea1a` |
| [verification/tests/KillerIntelTest.java](../../../verification/tests/KillerIntelTest.java) | `ff0b02aeffd1fecd10ede551e7fb90802d6369a7266945f5c0a577c83652fba0` |
| [verification/tests/ManualDeathDeployTest.java](../../../verification/tests/ManualDeathDeployTest.java) | `4bbaa06c70ff46db6f280e0de9834cd4144dbdb7ddf58e7a9794d404b79240d0` |
| [verification/tests/RagdollOptimizationTest.java](../../../verification/tests/RagdollOptimizationTest.java) | `b857a09c9c1a4f226c4526466a802cabd5143c6da4a656ea12a66cbe65ac4be6` |
| [verification/tests/RagdollReadinessTest.java](../../../verification/tests/RagdollReadinessTest.java) | `122bbe6e0f39123f5dc17ddeffd23df1a5d585b7660ba6089841171a3461ed0f` |
| [verification/tests/RagdollScopeTest.java](../../../verification/tests/RagdollScopeTest.java) | `61d7b5096a75fe584cc11be67d26b68e3df2b63329f83a02c0b086a28adf3a91` |
| [verification/tests/RagdollSubscriptionTest.java](../../../verification/tests/RagdollSubscriptionTest.java) | `4c9f905237c035515c6fd2df6f97697e80e6a1984b3e2c4dd1b0ececd5b489d9` |
| [verification/tests/RealBoundaryMixinTest.java](../../../verification/tests/RealBoundaryMixinTest.java) | `cfb49e00389c22975605c4f0539dbe283ed219c7593ac64769ca4b880991be08` |
| [verification/tests/RealBoundaryVehicleMixinTest.java](../../../verification/tests/RealBoundaryVehicleMixinTest.java) | `788e2cf32a01ea63679b2c8ca4ffc2f152e5bd4e87bc61f74b7192ed3cbe5ee3` |
| [verification/tests/RealDeathEventBusTest.java](../../../verification/tests/RealDeathEventBusTest.java) | `da272a5d5bb03f906b3859574f01481e9033616937ba2fc4e7f2d02e613fcd02` |
| [verification/tests/RealDeathFilterMixinTest.java](../../../verification/tests/RealDeathFilterMixinTest.java) | `1bf6be82d9746736ef502d52e92aad239fb4573bdcb3cf87c6415fb492fe6169` |
| [verification/tests/RealKillerCardMixinTest.java](../../../verification/tests/RealKillerCardMixinTest.java) | `5b5108e00a443c371ecdce7dd4cafcc36bf339b11064c95cf544d3c4b0bf2e29` |
| [verification/tests/RealMomentumCacheTest.java](../../../verification/tests/RealMomentumCacheTest.java) | `fe5219f1c08b4f826822c759db6a11cc30242c3c7fa53bf5c1875b0d3ec9499c` |
| [verification/tests/RealRagdollMixinTest.java](../../../verification/tests/RealRagdollMixinTest.java) | `a73cfa08e2b15aa63886065afd5136052a3ad7b10e3bea4d5afc52c04c3dcbd3` |
| [verification/tests/RealReplacementPipelineTest.java](../../../verification/tests/RealReplacementPipelineTest.java) | `745e5e3810431fb7b5e2d05d6ce3be3af80e70a9660291e3a4a3fde81ee51e2d` |
| [verification/tests/RealRevivedProneReleaseTest.java](../../../verification/tests/RealRevivedProneReleaseTest.java) | `8730bb3bf09a5d4bc32b502162e9e592b74df1cdb22ac852826bc09152e82134` |
| [verification/tests/RealScopedPacketTest.java](../../../verification/tests/RealScopedPacketTest.java) | `7b2fd1da5430cd769a1a55ff91116bc438de7bc4c4f73a7014fd4764d273902f` |
| [verification/tests/RealSupportMixinTest.java](../../../verification/tests/RealSupportMixinTest.java) | `41fdc65ef6d867533f68be780962893e709254712521668d61b388335687c71e` |
| [verification/tests/ReviveCopyScopeTest.java](../../../verification/tests/ReviveCopyScopeTest.java) | `b6c683e278da06dd7ceadf6072606e8c7eecbf32d9812b10498ce23d7ae4af90` |
| [verification/tests/ReviveGpuTest.java](../../../verification/tests/ReviveGpuTest.java) | `9f8457055d2a5dc002034affee6cb2ec9d16e299de89b28238cf8407925edc83` |
| [verification/tests/RevivePulseScopeTest.java](../../../verification/tests/RevivePulseScopeTest.java) | `68d666e56396bbb0061136490db72225fe4cc27b5ba0cb4610ec7716cdf618b2` |
| [verification/tests/ReviveScopeTest.java](../../../verification/tests/ReviveScopeTest.java) | `765abaa8ed043bfe844a3975fb2fb0cc39fbf41644a07bf2f18ea550c819bc69` |
| [verification/tests/ReviveTransitionTest.java](../../../verification/tests/ReviveTransitionTest.java) | `823c0f76f5446caa57ff1d73a642bbba837d7d271dc90805b2e407ed6235d6f5` |
| [verification/tests/RoomUiScopeTest.java](../../../verification/tests/RoomUiScopeTest.java) | `7494ace2e42fc17e5f154b7034986b64ee1ba711e7b79fb85262d0255d1fb803` |
| [verification/tests/SupportRulesTest.java](../../../verification/tests/SupportRulesTest.java) | `87054e28247439f7a76879bbd2abba0af23ad7489b618c90bd1dd35794c33e23` |
| [verification/verify-boundary.ps1](../../../verification/verify-boundary.ps1) | `90c295896654f7a16e3f5d044f9a9320dca153a315d2671690a7081271757d77` |
| [verification/verify-death-causes.ps1](../../../verification/verify-death-causes.ps1) | `2dccae37c5c08993b8d4f86f646117653e4fea6be307eac2db222fa04ff8bd68` |
| [verification/verify-death-eventbus.ps1](../../../verification/verify-death-eventbus.ps1) | `27045eb2df60fcc6bf2a0b29de895d803de422f60f0b5df5d1febbb884687c8e` |
| [verification/verify-death-filter.ps1](../../../verification/verify-death-filter.ps1) | `2677b60f19e8fb2293e7a0a205cd852fd6619ebbdae9e7e1698824d72370e568` |
| [verification/verify-forced-deployment.ps1](../../../verification/verify-forced-deployment.ps1) | `9f5f6130fa711bd215736d124cac36c351ba9d649cf6ae4f3dccb717f9277f68` |
| [verification/verify-killer-card.ps1](../../../verification/verify-killer-card.ps1) | `ff89897ee27984a57752c6363508645902a63a78363bec8da6c162f1510cc773` |
| [verification/verify-momentum-cache.ps1](../../../verification/verify-momentum-cache.ps1) | `6e1faba5b7a4f30d8668f8aacca977708e0f944c7022d1d99ea5df6decc53ab5` |
| [verification/verify-prone-release.ps1](../../../verification/verify-prone-release.ps1) | `3a4ead831694500caeb671d23a5c87b29fc345ac9eed7f4fe45a88f2635ccef4` |
| [verification/verify-ragdoll-integration.ps1](../../../verification/verify-ragdoll-integration.ps1) | `e64d2717c4b249d460c4363666e51dc8fd3f0bab1f88382ab542f06c56adc352` |
| [verification/verify-ragdoll-mixins.ps1](../../../verification/verify-ragdoll-mixins.ps1) | `8f49e09af46136daec70edf905593c4721de2c59bd785627bc304149336a40c9` |
| [verification/verify-support.ps1](../../../verification/verify-support.ps1) | `9f4afee3a225464b5a307c94bc84063ccd374c55b007500282ac889bacddc8a0` |

## 二进制与目标构建文件

| 路径 | SHA-256 |
| --- | --- |
| `gd656conquest-0.9.3-normal.jar` | `0bdfaee77d7281c550f0d107ca0763fb750b8e66ced4d4aafeba3ca022ca9ba2` |
| `dist/gd656conquest-0.9.3-ragdoll.24.jar` | `47bc5ed18e6071e93c3d3570a21bc18e36ab09a2c69b9f6828c429b8cff9c20e` |
| `dist/gd656conquest-0.9.3-ragdoll.24.jar.audit.txt` | `957203da0a600ac0eae6ba50b7a09d9f209cdee80a796e494aede23c8cc8425e` |
| `ragdollified-1.20.1-1.1.1-hotfix-RELEASE.jar` | `5a0e05ace4cedc88210c2e543520db17bd975ec118494eebd2aeef64963e22fc` |
| `gd656conquest-bf1/build.gradle` | `654d4658ecec23d5b9a79a38c3bf7076d591862630e5b535216e615203c55293` |
| `gd656conquest-bf1/gradle.properties` | `6edb47455440358df83f55d5dec6be14602f362c9a733bd2a41721ac9d1ac9ed` |
