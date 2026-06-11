var BASE_URL = "https://www.shibashuwu.net";
var HOST = BASE_URL;
var DEFAULT_COVER = BASE_URL + "/17mb/images/default.jpg";

// Dictionary: wzbodyimg filename -> Chinese character (built from 5 chapters)
var CHAR_MAP = {"02MsqJ":"进","0uS4xD":"子","0V3OC9":"股","1akEaA":"进","1QZcMa":"下","25W7Yx":"口","2E8cGW":"种","2osKP4":"热","3Nbz2K":"汤","3tQ3XF":"身","3tv5lR":"揉","3xWpKV":"液","430VvA":"下","445o0h":"道","4QFbSo":"长","4r4UUq":"嗔","4Xvjje":"点","5nlsYT":"养","5U8uFS":"身","6XVWYn":"廷","71IHUg":"凸","71OjII":"胯","79Cerj":"捏","7hoYmG":"皮","82Krwu":"色","8g4XbX":"根","9qgHPT":"入","Aj1qkS":"出","aulzzg":"裤","B0VM7q":"感","B4sHrA":"羹","B70D9Y":"进","B8NOQx":"汤","BIL3aJ":"酩","BIWkPI":"进","bTieNW":"涌","C15uiP":"子","C1Zig6":"美","c6TmE2":"美","caL5HF":"干","cblvvs":"做","CKgNTx":"唇","Co6pQs":"凸","D3lRLk":"处","d6LYhg":"高","dD7vyR":"深","diWkkJ":"暖","diYIrp":"吟","dl45LB":"凸","dR88TM":"蛋","DyGEhs":"吟","eGcX9K":"腿","EH5U7j":"喷","eKHgIu":"姐","eVYghg":"捏","EYsl8d":"头","ezptKD":"触","fItD7N":"胸","fUiL7X":"出","FxocFK":"饮","Gq41FG":"抽","gXyluJ":"廷","H4tq4e":"魔","hb0zAl":"道","hEJFXm":"养","HJof67":"鸡","HpTyER":"淫","HRnYSf":"种","hU2fy6":"菊","hxsnCq":"口","i7yscQ":"道","iAfDqP":"眼","ICqke5":"粗","ifoKeY":"出","igNn7J":"呻","IQlXen":"棒","iSzJ0M":"紧","JC0hj7":"胸","jcfjHI":"腿","JI7JLH":"皮","JUBTq4":"情","KA4uzN":"骚","kNxKc5":"精","krz68q":"虽","L2ksR1":"涌","lCeF90":"肉","lCRadE":"腿","lMGBO0":"液","LP6A61":"顶","Lw7Q9H":"射","LWOgCk":"物","mG7R7R":"口","mHsuQH":"头","MIuM4Q":"点","mLDrD4":"欢","mPcbFc":"皮","MrdyXc":"身","mTWtSy":"软","mWwYIi":"情","MzAKhy":"肥","n20L2U":"臀","N46dYd":"养","Nd3Zgb":"内","npr93b":"轮","nSaNDx":"奶","nSXWmg":"高","NWMJQM":"做","oEC3UM":"呻","OkVViG":"潮","P0YQ4b":"长","PIQTLP":"户","q32vO3":"乱","q8xsf2":"处","Q9RWfY":"妩","QqVK0B":"高","RfvFqA":"器","rkKq2T":"茎","RR4YFw":"乳","rRJkvI":"软","RSiFfJ":"交","RXHuXE":"肛","rXLMOe":"穴","s9TKC8":"按","SkD6ZG":"舌","sLKjsU":"串","sNqsTD":"部","SwwF7X":"深","tDTUOJ":"淫","TH2sXL":"花","tqdPlE":"长","tsy5qu":"滑","TXhTYn":"裤","u197E5":"浓","u20wtt":"咬","ul3i0s":"口","uSAy3X":"点","uSn7cH":"合","uwvUTT":"长","VckAQC":"春","vpZzIB":"股","vtE2Oh":"戴","Vvapmh":"下","W3RqrR":"润","WgBY4H":"顶","wizdPe":"体","WzOp03":"裸","x5zI4m":"精","X7TlHF":"体","XcJBYv":"潮","Xdmj2l":"水","XE49XK":"水","XfHT0X":"马","xPoIaD":"尻","y3mnbZ":"姐","y6Ku0H":"交","Y7MhRX":"姐","YB1HyO":"裸","YDCcYU":"阴","ykrSsv":"美","Ymqi1n":"头","YNxRDZ":"流","YODoVs":"鸡","yPjCem":"爱","YVWEww":"色","Z5f6Wa":"稼","zaw9hq":"乳","zg7ZHu":"强","zQ6gqJ":"逼","zxfQjD":"出","zZaUct":"奸"};

var CHAR_ALIAS_MAP = {"xHxXF1":"食","OlobAd":"套","KWhvzh":"拔","3Cn8ks":"蜜","YwT2rh":"根","eED9Kj":"硬","nqN6oD":"喉","CtJrEg":"摩","fEnbvU":"强","WyUgZM":"感","ASBJdA":"滑","EgkakO":"性","BAyOqN":"欲","rQ6kci":"宠","qbuqpb":"娇","sYWG3O":"露","TlB6Bu":"挺","Iojbe3":"挺","twvxWE":"干","3yV2SO":"粉","zwjTLr":"嫩","IJPC4r":"壁","q5ff83":"毛","2uxFuA":"肠","J5Xjtp":"湿","1K34Us":"根","q1nBjI":"龟","x0a3KX":"缝","GcOYxy":"插","JWbQ5e":"褶","iuZys5":"器","4kxe65":"殖","yrNmNf":"性","q8Ultg":"硬","qCnmWv":"爽","LWQ6oR":"爽","0lzWTA":"筋","0quHRe":"巨","281hEs":"跳","5rUAPK":"敏","BVlHUR":"被","Fp1qKn":"壁","J0YUNt":"蜜","P5zMlR":"屌","VqV3GX":"性","WKHlux":"湿","X48d1v":"肠","cXokCr":"擦","flSwe9":"吸","gA5Bc3":"汁","ldDgHu":"器","ni6MRJ":"浆","tS2RIi":"摩","tSSAod":"殖","vSmdaU":"津"};
var CHAR_HASH_MAP = {"3646d59242ec48851c5bb2420c8f327e13f81a59ca58fafccec93a048ff5a19d":"进","b2fc9917d287ced9282ebf2c1b216cafe921dc6f35b783af0988495566bb9557":"子","366a8730bd5fc25797e98622718e3e089f706c49d9c58e8830b1dfdbcd197e3f":"股","23cb90d853337e3c2cdca435d6b58d800461bc00ca8fe348be400fbbed6ceaf8":"下","ecc7f823a82f39524466a0fdfe03d88cc2dd41e933888d3927a803308ef72a53":"口","61ced806cea22d727110045503e58eb36a39aad80e6b4b53af0443d9e080fd95":"种","cd06a656aec8a772e60939238ba690ed47c653703606147f24c1f019c0983808":"热","98f3aca744c822ec3851a5ff7ce6bb3afb01316e09d8c59a7b340c2ac71cb53d":"汤","453f729767b0e60766704c185ff8cf744f1b2807c134a8c15afdad79b5f4d27b":"身","af29bee14bf708c68484f55232fe7df482839ac48eaa3980fa9d22973fae2cdf":"揉","31e6fd4908bc3d55d40c147c59a7e6c5278de61819a5586a416606293c83bd94":"液","66b97a24cfab86e413c9e15a05625af70bf2226d918a51e05ada1db3c5516c52":"道","160e9a091d2c1c9a09d0da8d4dc9725145c0f10ece1107055f2b6739595d2d4e":"长","f5d497138b3e1d79ebbb8b58acaf9d74d95f4f5ec729dd61196a78087d6957a7":"嗔","94e1e82d539646d96935c48eddaf6d02d0e260884b63e9e6950f2bcf590f07ba":"点","fe05bc7fdbd0d215bc3876b78cc6a10bffb5cf422a7e573d141ba407bc7aee36":"养","6f225dc9c1a18887afec80787370493fcb4c63dea45f0ce58d297df81f804a59":"廷","41f6ef2943475b47862ab0aedec6bbe40bc7abadfb2e1dd29f09c4653372ecca":"口","793693d162b7ae81c7b374dbf145eda95115f725fb9902e2d640c2cd830352f2":"胯","b39e3a0301475d59a42fae9ee92dd1165307c230f3b0ff3962e36d9fb85a93fe":"捏","84a903f6558d9c9d1f6a2f04b0dfcdce1ce11216d35727012d2aa4b920dca06a":"皮","559e521cbb7b744640e50521444ca96d6a6f0efa41584b6d97fe2393c901ed12":"色","5f335f09994a3e6e227b966eb02fdf96669e6b48828230bdf2d8115ca3aa17d5":"根","bd6772c3db120206367120b3d14b9675b9553be61e4000a43274c39ab0a74979":"入","f05f4837fd943ecd314148d1fd7469e2d19566b49f09494b9305b3b7d6d6987f":"出","2f87c8412e27934c6995ce68156c505a8f4581e30c3efe9756b9d785c1c1269d":"裤","50bdcf8e01c8a3567360fda87522984373eaa94dd640c1894cd4a67f7f1a8659":"感","4778a0bb56cf6cf33b1e6a348c6ad234254bb7ff5cdbf5820320eb802866a348":"羹","70f1b28daeb3007038a79dc4430b555e35047586dcf6ea5f8b73a27189c378fe":"酩","cf7097d2bfaaa6a4bb8b1e5c43cde4860696e8b5fae33c43f671d59324aad5ed":"涌","6831553de03e58629f0df433f63feb4324be1752bd2bd109470fb7b7b6fa6985":"美","48433a17f2bf9181c44b23acd56b7975a5878cf54ba04f1d31440a7c5c087ac8":"干","9efb56df84456e5544146bf6b6d3dcab698e48d685bb827c7072922fe496c4d6":"做","8fb65488d05c37fb9cd190ec96bc3876390424f0bb6b0c1a14bb202f8e40df30":"唇","6f6de2706f36b4b199db2b37c0a99038b80da4f5f7004dd688452c5c40c6825e":"处","dd11f9d429162b3334ddfbd70703c66bd1b54f2ecc8c5ba691581f8d24bfff1f":"高","061ce2a75b4149ff6d5ff75411212a05820d936c817826b8a70679af62da42a8":"深","29054598d25dd5ff7e97c0e0b4a2651435259e0d770ea833d10945c254ea0dcc":"暖","a5a0c0bab9ecb19551203fd6cdcca50768467ba475a3502fe1335e5ae92785fa":"咬","6cb901f7d362ca95a36462954df4f5701e30c72d201c1a7fbac8d0e978a3a212":"马","19f0a120ea9442d14820dd34daf2fd31c69f42d3e9b5c3001ebcd5c3c4e415e3":"蛋","0c0e7604b9c915c02a25ce771a00ee08a6ae08e6e3b955cb14120c728f93ba98":"腿","107143c1a91665cab667d07851af0650163bd399c8c3bf8385ac2b076b013485":"喷","24f38a6491cb2c4556c8948a297935630e9acbf747124fd535531836d98df17a":"姐","9ba4b9535c9d3ece7862e50adfc8801384b8a5f824d2a4fb3e5679e7bf27bec8":"头","5e6de4a04ce95051b8710c5693c969ce26b3c61e46ed8022da3c3a21fb7ac6c3":"触","7a5bfbc2759ce39a3829f56289b8f352c77e6ce7aac5abe33e6d7ce9c1ee4194":"胸","62500ac41b63164fa63aed7fa5aee4e75bd15f5bcf411a8155bd1e437f2e49df":"饮","734bf754927bd2b0606f706637618ee08853e2e90c92053a7c8ee1eb890123a1":"抽","28402ca475753519fe66a2405984c8bb52e0b6af3e5666585cf6a0da00d7540f":"魔","04afd56e495d4672b559a4f7173dc6e5e6cb85815a1e8c85367175c9bbf7f065":"鸡","fe3678a5be433dcd2cc51733b8ead5a5a8f5431a57a3bf29b018ad2a9066d07d":"淫","6b396a1c736954ac763d3f45c79cfd9dc24513316d1c05760ed6df56c54cda7f":"菊","5fc209781aaa00b4f31bfbf8d88409f7643460ef455321d6169437a3e4386456":"眼","b37162fd2558de0506cb26ab1ac2c0d3545167675d1dae0f1c994f3baf941348":"粗","99483bd75d95e8986c8e6e6605ae5309ce57c7f721e3df797457bb5ec034ad0c":"串","a4901861000997635fc105e05cc55886f8d641e59937168def78ec42143614f8":"棒","f6954bbab4588e2429a4c83a42927a0a14c6910fc91cbd3c8a18154de96e2930":"紧","c0a8c7416bc269196cd1cf2aa2adf0b894c01c90f9696d37379ec3fee7d7c6d3":"情","3167ee52270146aff1620c0f61805b6e352af2eb3a895ffb0dd87d5fa7d6c49c":"花","7c4b5e332efee0f587a92e0a6b7917de24a40fa147be1a737e7903104cbb0949":"精","f09fb30d83ab625af394c5e474b87c7344c7129815a1743cbb44d46873491bb0":"虽","4e818c7afc4134bde5ad074147337edc7e7058e95a614b1b4f7f7a7e32fbef9a":"肉","a2987e4d90c4654dd0a95cbc9fc7695f2985881c78607402f3a4fb4d74487411":"顶","0a54190418a0f66d7ff67d2260e36ba53553320ba74a1c231d4b48100cc3704f":"射","8a8ad3809d2bbeefb39c0988a6fff56780c1d304c21da4b2ad14cce06923f6d2":"物","331a3db5168d8b04376288fcd343185b1c38b106ccedd9404fb1f1d8d18648c6":"欢","a05695559c7f9a24458b13f4fd57a90557257b344cef1db27549f73466d6395b":"软","cb88394d6349fe6d74eb786cfd681537169a03b321d36502de53fca346d7cfc7":"肥","d989b43d213679f7c586151e8565eb6fc05db7e73d521090d07c44f149a8d769":"臀","0336bc23de84037a183e719d96cab59df9d3de7f085f10eb6230ea85d4b2ee61":"内","aabee6e896f66748640387f782f4e89fde923ba8ab33f77e358f1e3f77fbd237":"轮","47135e87a19bb9382ba5c62f86b6ce511c9c741d6b5fe48bace1c9331fcd8c22":"奶","ee0323f8d2b88dd809dbb0c487415c8abed51d38bd456cf0302e73591ad33b10":"潮","f4d2c5ea60494867a5e8ba2f1a0497b7e036af6a4183221690550c3b593f557f":"户","1db53e10ae8149e1aacf8593781fd98bd6194ba9d3bfb17d6a2be7f1591a2633":"乱","ad9df34fed6a07cb34f35d286d3bebc964acdc42a0744cea4d15d93764f93eb6":"妩","e397ddec6bcc4ab7189f9d947ec81fade917d6a34dbea8302a213a41ce5df6b5":"器","ca940eebad883599f33b273db9a392317de790735aeefe414f2cd45cf4941dc3":"茎","d6c8c8aeaf913e86fca18442fe8d134e75022f86b3adcfbacfdbe495c465686a":"乳","892dc2ea851f2facaa3f5cf18d922c4d34cb525a86717d2a43c3d37d622fe540":"交","9f460582c948841cb91a0de9ded72d78c35d8e7201905ab4b86d79bc596ddcb9":"肛","a8a6470c09796a815c0365f4e4b4e707897f3ccaf6d33003fbbefc3a40da2e85":"穴","bf2a309b3ee3ae80787393097b41787ee27d3d26fa46f5e343a21d893e320714":"按","406a9c72387c60451bef46dae489a92edd44dd2ee9e425f392e10834754cd689":"舌","f8c3b95b2a8cc1daa684fc23605c85237e44af87b3f4ce0f259a01c18232701e":"部","539d727f26a7801788f8bd092308e6043dda987bdb11963cb7f00674db6a34b2":"花","9eba726cca48dab7271a8db8094eaaf1aa71106805559622dba5e77b3d4bce74":"滑","13607f40f41b6ceafda29ba5026c95058286984a7fa0f8b704ca1adee373e74d":"浓","ee105184c2321050d764df5fe80be222cbb81c88d175c6aab664b340ab7fefdd":"合","e29f53f0a0173fde8cce1aacc6db335fc25a312555e5216e4ba87f044c195c30":"春","a0d1aa422fd9d8f1f44e1a58ca38a044c6a8f22a5a3455e05cdfed739a23bd0b":"戴","55b8d200f93019cd3a668f6abe2cc57480052d66337bccf0f8f69d702b76e8ee":"润","11d726afde33c81ddecea7bc2bb48548b1cb183528a48e440488659aef482419":"体","9cdea219ee8be82ead1f20ed182a1ed9483f5b421069b4982c1c1d05a7183232":"裸","a9a86870b1d2dc52b9692b7cc60a5c9116243dd79343e477d482b4f729c59156":"水","8de9878b57643fcf74a3444c7bff4bd0c7999435d66cf9e55cd3b5c5c9e79953":"尻","ae3a979b51f8b059262a087b07690231b6a47679b39249700b47bfcfa3a9a7f7":"阴","68c785170d4e325f294f47e32b5003b36bd0f3768939437074a398d91520a972":"流","508edfc5f17abad8e7bc605b37a7b2c748866ec8d6cb9a08d25b02eaa9af9cec":"爱","b99aa4d3ebee1ee4f2ac7e21c70a2d52f1b58201cd4006aa101c5a6d9c43f94a":"稼","d28fe2756c30ad4a94525e2ff730964e9d644d9575b4fa416863ac5480c4a933":"强","bc6ff4a23ae3f82781a238c4dcbd944dc93fdb10c29a26191b54f29471a399ec":"逼","017a55f81135f15fe363193548c231b3cf935da4847d0d01277689183c22e717":"奸","0d201a282bf9ef2afe44aade8d994833799b647e3f9f029d0e1cac17ece6e490":"菊","71b8a19293903ca33ad5b7133a43958c6f6462b0e273ac6eb6adbfc9e5264ff3":"腺","d1b5c0b3dddda3bae6245c755c99e6c91cccef7b9b6ac6f6d93626aeddb9e13e":"睾","3cc9eecc39896391bd48bdec35f6ad6bd313e5848da931b270188f1beb4df1c9":"蚌","42078df1c390f01cd167bf418ed0dd2da3436395399999e2c6713017c77b9f75":"屎","d8e1253015f8300f87f4686250bb08d39ed5fc981adb212379efa7a2f279dc6c":"离"};
var SHIBASHUWU_CHAR_ALIAS_MAP = {
    "0HGxQk": "深",
    "0niI3T": "内",
    "0wtvZe": "妩",
    "17dyvF": "子",
    "1Q7J17": "尻",
    "1QY3Sx": "肥",
    "1QlmXT": "入",
    "1aNhq8": "水",
    "2HQdgP": "干",
    "2YVuQw": "鸡",
    "2eyoUm": "情",
    "2wRNru": "浓",
    "32eg1b": "潮",
    "344Nml": "合",
    "34CdQG": "美",
    "3g172U": "阴",
    "3u3M78": "抽",
    "4Fyhwz": "魔",
    "4sihu8": "情",
    "4yQjjb": "干",
    "50AwvR": "交",
    "50EVCv": "种",
    "5JtBLe": "姐",
    "5O4tiU": "棒",
    "5iCNae": "茎",
    "6BmY3I": "合",
    "6GQeVB": "高",
    "79mAmX": "润",
    "8GGAEd": "股",
    "8L0pMx": "水",
    "8YTeK2": "流",
    "8lzlVN": "串",
    "8nVwax": "流",
    "8nwaeD": "感",
    "8yr5TI": "春",
    "8z8f3w": "肥",
    "96jAL6": "捏",
    "9VhQ2s": "爱",
    "9eqV3X": "滑",
    "9kZrKO": "入",
    "AA8lkK": "唇",
    "AAfaVX": "肉",
    "Ak4ZRQ": "逼",
    "AtXg0W": "轮",
    "BP6grW": "臀",
    "C7GxZ3": "点",
    "CJCmeD": "热",
    "CSXyTS": "乳",
    "CXwVIE": "处",
    "ClD4Jt": "暖",
    "D0VCty": "乳",
    "DAB2zz": "紧",
    "DNCj8g": "阴",
    "DPBdcs": "体",
    "DW415W": "欢",
    "DkhSyE": "做",
    "DwBqgG": "粗",
    "E2XPtq": "蛋",
    "E8kQQv": "虽",
    "ECYzPT": "马",
    "F1UfoW": "奶",
    "F72b9U": "润",
    "F8yeV4": "器",
    "FCV5p8": "紧",
    "FElCSY": "涌",
    "FPYzEi": "户",
    "FgpdyB": "稼",
    "G9bYIk": "胯",
    "GK9n3m": "花",
    "GOdNnq": "茎",
    "H16Zi0": "裸",
    "HEjjjY": "子",
    "Ht77rK": "眼",
    "HxYHEN": "稼",
    "IWEbMv": "根",
    "IY1ueI": "头",
    "Is3075": "淫",
    "IybU24": "花",
    "JKv5FK": "股",
    "JMQszf": "根",
    "JWu62W": "胸",
    "KCd29b": "汤",
    "KG0uV4": "蛋",
    "KlcA8L": "液",
    "Kn880m": "户",
    "LMkoiL": "按",
    "LS0sMs": "轮",
    "LY2I8H": "紧",
    "LmEOAV": "裤",
    "LwrG3K": "裸",
    "M7Rfaq": "舌",
    "MVtLvQ": "咬",
    "MsN91l": "器",
    "N5Nsks": "穴",
    "N71rG7": "舌",
    "NUPZRu": "肉",
    "NepwtW": "舌",
    "O0piK4": "器",
    "OFVEcH": "爱",
    "OTUmUB": "戴",
    "P0FCq6": "流",
    "P704Pf": "色",
    "PEuBq4": "捏",
    "PPwcIx": "穴",
    "PiP8nz": "色",
    "PrDrLf": "顶",
    "Ps59WA": "喷",
    "Py7qxN": "棒",
    "Q0YQDI": "尻",
    "QiumNC": "下",
    "Qj5sQw": "眼",
    "Qknk6f": "浓",
    "QwrEN1": "揉",
    "RWKsGL": "射",
    "Rkv4Rt": "部",
    "S7PfNp": "精",
    "S8RESs": "春",
    "SOG1IX": "按",
    "SSLTc5": "强",
    "Sw5lNg": "乱",
    "T59jA8": "唇",
    "T7hWwP": "肥",
    "TBhiWH": "潮",
    "TG1zFK": "羹",
    "U1Xyyz": "粗",
    "U98PM5": "喘",
    "ULgU3U": "入",
    "UpBXUE": "润",
    "UwFek7": "深",
    "V15h1D": "淫",
    "Vew5Xb": "暖",
    "VoCYWE": "酩",
    "W4LawG": "胯",
    "WlN5Tx": "奸",
    "XrbXYF": "暖",
    "Y6jrQh": "触",
    "Y7DEGI": "物",
    "Y9si8O": "奶",
    "YCpUba": "酩",
    "YGyhOF": "乱",
    "YOPvkR": "交",
    "YPHbQI": "肉",
    "YW4LMK": "软",
    "Z6EUa4": "菊",
    "ZB0P6N": "魔",
    "ZjwkG5": "户",
    "a98bTg": "浓",
    "apvWdv": "喷",
    "aw14wH": "皮",
    "bMeh70": "棒",
    "bbP3wp": "臀",
    "bdpzSn": "裤",
    "bm7oFG": "喘",
    "bsaQhL": "干",
    "c8tK4d": "物",
    "cRorMs": "热",
    "cqHjLH": "爱",
    "dGjBLO": "蛋",
    "dd2Pe4": "强",
    "diKmtw": "揉",
    "eMxU2J": "部",
    "eSDqg1": "花",
    "fF8lJB": "身",
    "fKxN8s": "滑",
    "ffuEUB": "唇",
    "finzFu": "粗",
    "g0za8Q": "触",
    "gB092d": "抽",
    "gDcIrm": "花",
    "gHKKvQ": "茎",
    "ghAvhO": "眼",
    "ghd1tZ": "逼",
    "h6pxXB": "臀",
    "hjqEpj": "饮",
    "hy3l9S": "酩",
    "i6JK9D": "内",
    "iEVsNU": "抽",
    "iV9X7h": "液",
    "iZXmaJ": "体",
    "ipPYTJ": "逼",
    "jC4QLv": "内",
    "jJdqvR": "奸",
    "jN6a2M": "涌",
    "jP3XFs": "按",
    "jTRhtM": "胯",
    "jkIbW7": "处",
    "kTZaFW": "触",
    "kjK3cV": "奸",
    "l87mzC": "做",
    "lfNqgI": "花",
    "m8bIOH": "腿",
    "mIVndN": "菊",
    "mbPwgQ": "滑",
    "mf9sjf": "稼",
    "n2ugAW": "热",
    "nJxDGr": "射",
    "nLk3Gj": "虽",
    "nTNqMg": "口",
    "nVh7ED": "鸡",
    "nbFts0": "道",
    "nmdLR2": "乱",
    "ny1YRv": "感",
    "oSos9v": "揉",
    "ogqMaU": "物",
    "os7zep": "羹",
    "pbTMrS": "欢",
    "psQzBV": "喘",
    "qPILM4": "口",
    "qPNGVn": "合",
    "rOf0QG": "顶",
    "rR8VXH": "奶",
    "rbBm2v": "养",
    "rhFmDv": "软",
    "rl8BAJ": "魔",
    "sIPdnn": "汤",
    "se7bio": "羹",
    "sfKX6g": "射",
    "sggj40": "马",
    "stvqgD": "尻",
    "tQ538Y": "春",
    "u1myy8": "种",
    "uldnbM": "戴",
    "wck1mL": "部",
    "xH4aHz": "菊",
    "xLnPTj": "妩",
    "xWhy3c": "戴",
    "xfj03L": "饮",
    "xh6HKs": "精",
    "xhzPYa": "穴",
    "xiW8xS": "欢",
    "yFY6eb": "轮",
    "yiXDfC": "根",
    "ynyqSF": "阴",
    "zGLGfD": "虽",
    "ze0gS6": "喷",
    "zfFf2r": "胸",
    "FbTBG8": "菊",
    "FusgMU": "肛",
    "ysFpNa": "肛",
    "FLZ3Eo": "肛",
    "H1Alv2": "腺",
    "b3tODi": "睾",
    "wBoTVa": "睾",
    "lfdHdB": "睾",
    "eBPb6h": "睾",
    "dMSZER": "蚌",
    "cSRWr4": "屎",
    "Zh2Rhp": "屎",
    "dXQaWU": "屎",
    "EwfSaz": "屎",
    "nmavUm": "离",
    "4SwMDC": "茎",
    "7dOxQV": "茎",
    "iWr22I": "茎",
    "uydlvz": "茎",
    "04GCML": "庭",
    "KWbp69": "庭",
    "L6kVN2": "庭",
    "TWMajn": "庭",
    "LGmXda": "管",
    "SNe2HT": "管",
    "VW46Fu": "管",
    "ixvpF5": "管",
    "EWhbh7": "牛",
    "Lu0CqB": "牛",
    "Ml8Enh": "牛",
    "l00ZM7": "牛",
    "0c4K4X": "勃",
    "I9s9m2": "勃",
    "PJi8ZL": "勃",
    "eTpxaj": "勃",
    "SYEynP": "核",
    "k7uow7": "核",
    "r3zlf1": "核",
    "ruorow": "核",
    "7s0V7z": "享",
    "TF0BNl": "享",
    "kOu0jZ": "享",
    "pgYK5n": "享",
    "1GQRah": "李",
    "bXvJpt": "李",
    "nkNzPF": "李",
    "Rk21ZO": "融",
    "RxiYi2": "融",
    "fucfQu": "融",
    "EmnbXO": "擦",
    "ki3Fpu": "擦",
    "rfc9Cg": "擦",
    "NoMgIN": "蕾",
    "SKabbZ": "蕾",
    "jljWnf": "蕾",
    "CDOCjl": "敏",
    "qA9gej": "敏",
    "z4eRHV": "敏",
    "5yarDt": "坚",
    "H1sTWb": "坚",
    "Ooz4tZ": "坚",
    "1Q7DFM": "阳",
    "iRuJt2": "阳",
    "wsSQAm": "阳",
    "B95AZ4": "思",
    "OyCuCo": "思",
    "W1QMrf": "思",
    "3pBO7i": "跳",
    "6JeVld": "跳",
    "6rZBjS": "食",
    "EnE8rH": "食",
    "yckQog": "食",
    "AIZChH": "跳",
    "BLbFdw": "蛇",
    "wEtN0I": "蛇",
    "C5kpfy": "毛",
    "TEb7xk": "毛",
    "aiiSqB": "毛",
    "C7UhUq": "励",
    "kaFePE": "励",
    "CINlbm": "膜",
    "Dp3LCx": "膜",
    "NZENQT": "膜",
    "1EDK8s": "督",
    "OUT207": "酥",
    "Q82q72": "酥",
    "y8dJaY": "酥",
    "aeWwZr": "具",
    "rOZ5uF": "具",
    "wq8Y1k": "具",
    "HAMIYA": "插",
    "SuNPNf": "插",
    "V8Eyfo": "插",
    "IuTJGP": "龟",
    "V580wu": "龟",
    "fqyKcS": "龟",
    "CmqfJd": "洗",
    "Er02iI": "洗",
    "8TJtxH": "腹",
    "yVhkwB": "腹",
    "EBwzVe": "抚",
    "qr4SyJ": "抚",
    "gNBaNy": "洞",
    "m6xDZM": "洞",
    "OBxASM": "肿",
    "g1EGIJ": "肿",
    "VITXtA": "尿",
    "iWEyBy": "尿",
    "qV4vOr": "尿",
    "39Q4oG": "滚",
    "Fj3W5J": "滚",
    "ZIIMcG": "滚",
    "LmhnpW": "吸",
    "Utenri": "吸",
    "wvtuWv": "吸",
    "ACdLZ9": "巨",
    "clEq5k": "巨",
    "XA4MjZ": "宫",
    "e2wm7D": "宫",
    "VbJF1T": "雄",
    "ro00zH": "雄",
    "VDWzPU": "瓣",
    "sCVJNg": "瓣",
    "HaYKsi": "操",
    "LcpsgK": "操",
    "DarSsa": "筋",
    "X7kpqr": "筋",
    "Il4TMB": "汁",
    "mg4OXy": "汁",
    "LnTQv2": "孕",
    "urG3KC": "孕",
    "NJz6jW": "硬",
    "TmSgYV": "硬",
    "6nj6Ie": "宠",
    "ATTX7V": "葡",
    "HmKboZ": "伦",
    "LcFChb": "殖",
    "MVQcp8": "湿",
    "TG0ZKb": "性",
    "TsusrF": "器",
    "vK7Jvx": "器",
    "duIc3f": "喉",
    "sslEmn": "喉",
    "ePA8MY": "柱",
    "hMWfJ8": "柱",
    "lMMaex": "根",
    "niqgBz": "根",
    "nBX8jj": "撸",
    "teHY68": "蒂",
    "dAkSUt": "摩",
    "jdM9Hk": "吮",
    "yi3kvk": "吮",
    "kvdXq4": "嫩",
    "mbruIA": "灌",
    "mbuFm2": "壁",
    "tkrkpX": "泄",
    "zHpTox": "泄",
    "uEfWUU": "丸",
    "ucBeKF": "蜜",
    "OQTfho": "腺",
    "Io0RAk": "蚌",
    "T1dbCx": "丸",
    "02cjmY": "烫",
    "gIgS4H": "烫",
    "0PoVDw": "雏",
    "KcV7nQ": "雏",
    "pHjELQ": "雏",
    "u6HCsM": "雏",
    "1IdcYE": "蕊",
    "cTW2ZB": "蕊",
    "e116Xt": "蕊",
    "gNlr3l": "蕊",
    "4g3jub": "妓",
    "72f0sM": "妓",
    "AQNHZK": "妓",
    "plDYVo": "妓",
    "5F9sUO": "肠",
    "iXluFo": "肠",
    "7sdvnm": "奴",
    "9VA4mK": "奴",
    "ElfrrA": "奴",
    "QEJe5h": "奴",
    "JyjO2Q": "肉",
    "KGVTw1": "肉",
    "hCynpZ": "肉",
    "vltb4n": "肉",
    "LOfygv": "邦",
    "XCoNMG": "邦",
    "nKd2T7": "邦",
    "qJXT7j": "邦",
    "Zj5jOj": "屌",
    "ks2lRq": "屌",
    "t6yHEf": "屌",
    "lJzcE6": "拔",
    "yP0lFN": "拔"
};

var IMAGE_CHAR_CACHE = {};
var IMAGE_CHAR_MISS_CACHE = {};
CHAR_MAP["gXyluJ"] = "挺";
CHAR_MAP["Q9RWfY"] = "娇";
CHAR_MAP["KA4uzN"] = "露";
CHAR_MAP["4r4UUq"] = "喘";
CHAR_MAP["U98PM5"] = "喘";
CHAR_MAP["bm7oFG"] = "喘";
CHAR_MAP["psQzBV"] = "喘";
CHAR_ALIAS_MAP["4r4UUq"] = "喘";
CHAR_ALIAS_MAP["U98PM5"] = "喘";
CHAR_ALIAS_MAP["bm7oFG"] = "喘";
CHAR_ALIAS_MAP["psQzBV"] = "喘";

function mergeAliasMap(sourceMap) {
    for (var aliasKey in sourceMap) {
        if (sourceMap.hasOwnProperty(aliasKey)) {
            CHAR_ALIAS_MAP[aliasKey] = sourceMap[aliasKey];
            CHAR_MAP[aliasKey] = sourceMap[aliasKey];
        }
    }
}

mergeAliasMap(CHAR_ALIAS_MAP);
mergeAliasMap(SHIBASHUWU_CHAR_ALIAS_MAP);

CHAR_HASH_MAP["6f225dc9c1a18887afec80787370493fcb4c63dea45f0ce58d297df81f804a59"] = "挺";
CHAR_HASH_MAP["ad9df34fed6a07cb34f35d286d3bebc964acdc42a0744cea4d15d93764f93eb6"] = "娇";
CHAR_HASH_MAP["3167ee52270146aff1620c0f61805b6e352af2eb3a895ffb0dd87d5fa7d6c49c"] = "露";
CHAR_HASH_MAP["21fb00bc49b1dac6a8228f0575662335b121f36a7839bd6596ccea166886c3f8"] = "茎";
CHAR_HASH_MAP["3fde3e7f83a74aae6d44aa820f3ad2a8069c43bf7c1033b95bde3e11700fc1a0"] = "庭";
CHAR_HASH_MAP["61903b0b4512e21c53563b9180542bb2bd3937b774a0b536b27f233cf40c2b93"] = "管";
CHAR_HASH_MAP["6666f7042226b8f0bab07dec79963a8b23234ce53081473361b35c3dbfe036f5"] = "牛";
CHAR_HASH_MAP["6d39c37ca6f77f2721f33ecd438b098912ddddfa22a2eb75a405e873f47245de"] = "勃";
CHAR_HASH_MAP["bad403e3f7a82dcd6e6b5e69788f12486258e83f0bebdc375a829441ee236e70"] = "核";
CHAR_HASH_MAP["cddb8181e3111041d7ceb4418914b8cb37f69936101825339dd004570ae5efa4"] = "享";
CHAR_HASH_MAP["20cca0659640a3f0f7bdcfab288f729e8843825258fdaaa3752eeee3fd18835a"] = "李";
CHAR_HASH_MAP["2b5f524dcb296ce178f5a6a43858a1fd0a625eff9e8c4f96e8d4a66c19898089"] = "融";
CHAR_HASH_MAP["45d64521b93c849dc73b8b4500d9ac42caa01c1d08c59523588ed0d8b4f5f624"] = "擦";
CHAR_HASH_MAP["4dc5400386deeeb185102ae44b385d7f73abf46c6d375a6f6bf1a6ca39b4055e"] = "蕾";
CHAR_HASH_MAP["50a642eef6fee437c0d6a063cada4e3cde9ec02db4a479655484d9514ba9835a"] = "敏";
CHAR_HASH_MAP["53d97e5d8c73bbef595a368cebea3e82f6323f6af6c63fd06bf1454efc17f3e4"] = "坚";
CHAR_HASH_MAP["54297c6d29c5d4da0b0034d47b96ca632af22b3c446044692740085a60bb6e1b"] = "阳";
CHAR_HASH_MAP["57e70ee1f72dd28643c79c31d14cbbd629176f99e800b91362154062c81a07d8"] = "思";
CHAR_HASH_MAP["3b551c23e0df4a677e0314338bde93e3bbc44fff28823363aa86a45da4eb44bb"] = "跳";
CHAR_HASH_MAP["ba586ed5e0e685753eb00b4daa833ea156d7388b93f536b5b196e73c7d5f2de5"] = "食";
CHAR_HASH_MAP["c074087f0944741c227d5206575c1d137aaffcd00ffd7ce41f578e7bf3270284"] = "蛇";
CHAR_HASH_MAP["f3524580462305fec8e20cb7a7457c2c56d27231812da63720aeac26d66ab447"] = "毛";
CHAR_HASH_MAP["8f7f3a6194138b2836fb55ad56cc4a936f7cf0efbf121cefa4f321a4459848ce"] = "励";
CHAR_HASH_MAP["8c326b8cc6231a9494971cc5e661443bb895a9b50e92b6ae85f4129d01fa17aa"] = "膜";
CHAR_HASH_MAP["f155a778b1366744c1c3f9a0f213de1068f7d31f070f08f892c6067a96cda6d9"] = "督";
CHAR_HASH_MAP["a9e2fd785c7663f17abda2671eb10a0822ee200d3f32308312e0b83bbb88598d"] = "酥";
CHAR_HASH_MAP["ce02f1df83119cafd3c34341c3d314ee7ed4941e67ada1f39177da0ef557942b"] = "具";
CHAR_HASH_MAP["e4f4fa020dae7a812b75e9cb0b4aea054d9b8ad68e5105e46e5524caed4920fd"] = "插";
CHAR_HASH_MAP["f39991f56f9356867785c1683ed61e30c4bd290840fb0dac094d6423d9823599"] = "龟";
CHAR_HASH_MAP["4ce7083447f19e7a8b989d54906841f25ef3ce3e8ef30cb53500f1ddad0d65cd"] = "洗";
CHAR_HASH_MAP["692c5bcb330eebcae36b917f16b7b110a5e19a9b5ad885eb7eb218f6be0c435d"] = "腹";
CHAR_HASH_MAP["779dc07397f65af17a0bb4bfd4215925e95666d54e147cef52f80d7c312a9d58"] = "抚";
CHAR_HASH_MAP["a9bea616b474963b3806ebf58c77fbbc37d75fd9b67dca43bfc6ec59741d0711"] = "洞";
CHAR_HASH_MAP["adeb7717e68720161194857e39fa7016c077c8f937511e217c91f733aa387775"] = "肿";
CHAR_HASH_MAP["275e69724950d6f426742e96a046146dcc73b0747494c38cedfb46d1e22aa1d9"] = "尿";
CHAR_HASH_MAP["908c0a5e51ef29b5d507af7183dc20c8987642711f16e2a2dd45e4a7466832c3"] = "滚";
CHAR_HASH_MAP["ae415bf93e09cbea3e2cbf8da7b44dfbd33655bf850147be349f410240acf92b"] = "吸";
CHAR_HASH_MAP["0b7819676b1a667fdbfb98a2b5297cc41e80ad8ca5de05418cabc9dad661d639"] = "巨";
CHAR_HASH_MAP["1be26cf5867829ecac9022f8d6ab0b73d86611425ff6c23412b1de081308ba2a"] = "宫";
CHAR_HASH_MAP["4b17818f4574737417e5ea866c1d9ddd83119069929c4afeaaa6f4e93c3e4ad7"] = "雄";
CHAR_HASH_MAP["9b9f0326a5bf49093cd11a0b4160b041925663bdeefcd25b96cd148723b5cd1c"] = "瓣";
CHAR_HASH_MAP["e6b79720ffccfc0f4fb2e6dea1e628e22f8ca96ab60a3394bc2b4fe34339fd5d"] = "操";
CHAR_HASH_MAP["e742eb8366cc3d61cfb9ee1fd7772ceee86615c4a3b2f29675a9e1edcb7f4439"] = "筋";
CHAR_HASH_MAP["bfb5e798c5a425289fce8832545f23ba8cfa2fe1888d7828a9a65f817c8a0449"] = "汁";
CHAR_HASH_MAP["ed130c0b99a4bc04a7a6777ba451bd044bfbea9d2ed95497272e20a7a42e74df"] = "孕";
CHAR_HASH_MAP["f8ef5b2c65f22c2c4f4cb9b4d6d59195b1461a593bd3642c8d4c710e48a2f826"] = "硬";
CHAR_HASH_MAP["9f5ce0b7bab5ccdba57bf9b048dbf6a72b5bcbc371de07337947bd74f7b00127"] = "宠";
CHAR_HASH_MAP["afa9e97c3c40ce699f48b2597b8c7c4bd2ab9c2e30a5703a1335d73371a4242c"] = "葡";
CHAR_HASH_MAP["8dd26134d9f6bff50774d535f8493620cb6c4aa0e75e6409a57997834e4d7165"] = "伦";
CHAR_HASH_MAP["8517a724b5c8f5ae895124bdbc66f8f8024c487ca43499e15fb9b864ff9ee230"] = "殖";
CHAR_HASH_MAP["64c3634dde5eedff53b8670be20a84d99f6fa3c88eae84743aa4aab172076bd2"] = "湿";
CHAR_HASH_MAP["90d7a69e14acc13147167d9f88c2f2a6662f1bf75d2205112126ea59a5282b03"] = "性";
CHAR_HASH_MAP["e844abf992283b17f550e5bf3435fc2038700fc1375076364a2bdb4d92949678"] = "器";
CHAR_HASH_MAP["887e6472290ccc89405a360a0ee217c935f26a0033e9aafc0ee9f6a961777c28"] = "喉";
CHAR_HASH_MAP["dc6ff5c194e11b6a0d3de571a8f3be894d6bb23b57508b1b14bef71cc0f4f3a8"] = "柱";
CHAR_HASH_MAP["6fb90fbf6f69bbba1a08b09e287c2642f8b6d0aa1543ab4431a3f9f8344b627e"] = "根";
CHAR_HASH_MAP["39795ab31e5fe8270113e4d0b77596e3da66d234e52f6bd50c0deed9356f4be5"] = "撸";
CHAR_HASH_MAP["a68d6cb1895cddaa93b5ab2081f7f54c6416ed9082c298ef1e53b1556f9417d6"] = "蒂";
CHAR_HASH_MAP["8a41e8add96ddba2f9d2023f7ff9b24c5ba62758178613b6ba444476c77c06e5"] = "摩";
CHAR_HASH_MAP["dfffcc581fff86d9ee65e111f3f4df8d808c1f444e582453a0231dacd466a421"] = "吮";
CHAR_HASH_MAP["c56189c6bc6c93d2c7d5c82f25d27e34401f4262c372b65d4c56adc3a234c935"] = "嫩";
CHAR_HASH_MAP["7b4bd8e7eb6e75635a2d8acbd6d227c16527f4c2288bd0396a9b332f6eff9036"] = "灌";
CHAR_HASH_MAP["e459db1e5abc29af4e8f7d11cd34b9de7e1e249832c957ec4ec9b47ab8e7773d"] = "壁";
CHAR_HASH_MAP["b6102f9f7f84b53e49dd8670ef62581bf0ea9797916b4616f47e5704252306cd"] = "泄";
CHAR_HASH_MAP["09274ab234709298599bea5ae10da3a9fcf0b435b2feb00a851d2303e4f7f344"] = "丸";
CHAR_HASH_MAP["483b14f8ae5936f3a76bc5b5cb06eeab492a84d3953d8aa34d2937cebfa0e966"] = "蜜";
CHAR_HASH_MAP["a17d40ce04be812f85efd3a591ec13101343a88c2cbcc748e61962f77f0714d3"] = "烫";
CHAR_HASH_MAP["95c06a92b8afafb083737016944a282d343660e1d4ac7a1e1d543402ab585281"] = "雏";
CHAR_HASH_MAP["1c1fae8629c7132341a7f5a74a70212df1f83af474f615a0de252dc790572fa1"] = "蕊";
CHAR_HASH_MAP["a77a76571fcc882f88893a88d453bda9ce12ace2b604923581f375bb5e627d1b"] = "妓";
CHAR_HASH_MAP["260175d4030b4b8f9e98873a0a7a0a257521261d94357d6006d0ac76dfd56fbb"] = "肠";
CHAR_HASH_MAP["f09091bc81eeeddeb72af0d764fad2126986227aefbcd0a7ab0ef2b75d82fdfd"] = "奴";
CHAR_HASH_MAP["0d9aed6a6beb17f2c121910ab6d4c8308e2ef6419ccedd2fd78e2c7f1531e7d2"] = "肉";
CHAR_HASH_MAP["fb6c0bd99247018f678cf492f79109465c5ef0bd03ec8e9ef3856bfa363e7f60"] = "邦";
CHAR_HASH_MAP["7524aa43f50540f4c8412b41063aaad39d45e2689e268e07ba0df2347588dd6a"] = "屌";
CHAR_HASH_MAP["511bf1cb98080cf87c04467e29350a3912dab0c61994c746384ed6271386b414"] = "拔";
CHAR_HASH_MAP["f5d497138b3e1d79ebbb8b58acaf9d74d95f4f5ec729dd61196a78087d6957a7"] = "喘";

var FETCH_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.5",
    "Referer": BASE_URL + "/"
};
var FETCH_OPTIONS = { headers: FETCH_HEADERS };

var BOOK_RE = /\/book\/\d+\/?$/;
var CHAPTER_RE = /\/book\/\d+\/\d+\.html$/;
var CATEGORY_LINK_RE = /\/category\/(\d+)\/?$/;
var WRITER_RE = /\/writer\/\d+\/?$/;
var CHAPTER_CACHE_BUST = "v=19fix1";

var CATEGORY_TABS = [
    { title: "全部", input: "category:0" },
    { title: "完本", input: "finish:0" },
    { title: "耽美轻文", input: "category:1" },
    { title: "耽美辣文", input: "category:2" },
    { title: "言情轻文", input: "category:3" },
    { title: "言情辣文", input: "category:4" },
    { title: "女女百合", input: "category:5" },
    { title: "超爽辣文", input: "category:6" },
    { title: "男男辣文", input: "category:7" },
    { title: "浓情辣文", input: "category:8" },
    { title: "私密趣事", input: "category:9" },
    { title: "评书品书", input: "category:10" }
];

function selFirst(el, css) {
    var items = el.select(css);
    return items.size() > 0 ? items.get(0) : null;
}

function cleanText(text) {
    return (text || "").replace(/[\r\n\t]+/g, " ").replace(/\s+/g, " ").trim();
}

function resolveUrl(url) {
    if (!url) return BASE_URL;
    if (url.indexOf("http") === 0) return url;
    if (url.indexOf("//") === 0) return "https:" + url;
    return BASE_URL + (url.charAt(0) === "/" ? url : "/" + url);
}

function stripUrlDecoration(url) {
    return String(url || "").replace(/[?#].*$/, "");
}

function addChapterCacheBust(url) {
    var full = stripUrlDecoration(resolveUrl(url));
    if (!full) return full;
    return full + (full.indexOf("?") >= 0 ? "&" : "?") + CHAPTER_CACHE_BUST;
}

function stripHost(url) {
    var full = resolveUrl(url);
    if (full.indexOf(BASE_URL) === 0) return full.substring(BASE_URL.length);
    return full;
}

function cloneHeaders() {
    return {
        "User-Agent": FETCH_HEADERS["User-Agent"],
        "Accept": FETCH_HEADERS["Accept"],
        "Accept-Language": FETCH_HEADERS["Accept-Language"],
        "Referer": FETCH_HEADERS["Referer"]
    };
}

function bytesToHex(bytes) {
    var hex = "";
    for (var i = 0; i < bytes.length; i++) {
        var value = bytes[i];
        if (value < 0) value += 256;
        var chunk = value.toString(16);
        if (chunk.length < 2) chunk = "0" + chunk;
        hex += chunk;
    }
    return hex;
}

function extractWzBodyImageName(src) {
    var clean = String(src || "");
    var slash = clean.lastIndexOf("/");
    var dot = clean.lastIndexOf(".");
    if (slash < 0 || dot <= slash) return "";
    return clean.substring(slash + 1, dot);
}

function lookupImageCharByHash(src) {
    var fullSrc = resolveUrl(src || "");
    if (!fullSrc) return "";
    if (IMAGE_CHAR_CACHE.hasOwnProperty(fullSrc)) return IMAGE_CHAR_CACHE[fullSrc];
    if (IMAGE_CHAR_MISS_CACHE[fullSrc]) return "";

    var connection = null;
    var stream = null;
    try {
        connection = new java.net.URL(fullSrc).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setRequestProperty("User-Agent", FETCH_HEADERS["User-Agent"]);
        connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
        connection.setRequestProperty("Referer", FETCH_HEADERS["Referer"]);

        stream = new java.io.BufferedInputStream(connection.getInputStream());
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var buffer = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 4096);
        var len = 0;
        while ((len = stream.read(buffer)) !== -1) {
            digest.update(buffer, 0, len);
        }

        var hash = bytesToHex(digest.digest());
        var ch = CHAR_HASH_MAP[hash] || "";
        if (ch) {
            IMAGE_CHAR_CACHE[fullSrc] = ch;
            return ch;
        }
    } catch (e) {
    } finally {
        try { if (stream) stream.close(); } catch (e2) {}
        try { if (connection) connection.disconnect(); } catch (e3) {}
    }

    IMAGE_CHAR_MISS_CACHE[fullSrc] = true;
    return "";
}

function resolveWzBodyImageChar(src) {
    var name = extractWzBodyImageName(src);
    if (name && CHAR_MAP.hasOwnProperty(name)) return CHAR_MAP[name];

    var ch = lookupImageCharByHash(src);
    if (ch && name) {
        CHAR_MAP[name] = ch;
    }
    return ch;
}

function fetchRetry(url, options) {
    var opt = options || FETCH_OPTIONS;
    var res = fetch(url, opt);
    if (!res) return res;
    if (!res.ok && !(res.status >= 400 && res.status < 500)) {
        res = fetch(url, opt);
    }
    return res;
}

function buildPostOptions(body) {
    var headers = cloneHeaders();
    headers["Content-Type"] = "application/x-www-form-urlencoded";
    return {
        method: "POST",
        headers: headers,
        body: body
    };
}

function extractCover(el) {
    var imgEl = selFirst(el, "img[_src], img[data-src], img[data-original], img[src]");
    if (!imgEl) return DEFAULT_COVER;

    var lazy = imgEl.attr("_src") || imgEl.attr("data-src") || imgEl.attr("data-original") || "";
    var src = lazy || imgEl.attr("src") || "";
    src = resolveUrl(src);
    if (src.indexOf("/17mb/images/enter.png") !== -1) return DEFAULT_COVER;
    return src || DEFAULT_COVER;
}

function buildCategoryUrl(input, pageNum) {
    var parts = (input || "category:0").split(":");
    var mode = parts[0];
    var categoryId = parts.length > 1 ? parts[1] : "0";
    var path = mode === "finish" ? "/category/finish/" + categoryId + "/" : "/category/" + categoryId + "/";

    if (pageNum > 1) {
        path += pageNum + ".html";
    }

    return BASE_URL + path;
}

function buildGenre(categoryText, categoryHref) {
    var match = CATEGORY_LINK_RE.exec(resolveUrl(categoryHref));
    if (!match) return null;
    return {
        title: cleanText(categoryText),
        input: "category:" + match[1],
        script: "genrecontent.js"
    };
}

function pushItem(result, seen, name, href, cover, description) {
    var full = resolveUrl(href);
    if (!BOOK_RE.test(full)) return;

    var link = stripHost(full);
    if (seen[link]) return;
    seen[link] = true;

    result.push({
        name: cleanText(name),
        link: link,
        host: HOST,
        cover: cover || DEFAULT_COVER,
        description: cleanText(description)
    });
}

function parseThumbItems(root) {
    var result = [];
    var seen = {};
    var items = root.select("li");

    for (var i = 0; i < items.size(); i++) {
        var item = items.get(i);
        var titleA = selFirst(item, "div.book_img_name a[href*='/book/'], a[href*='/book/'][title], a[href*='/book/']");
        if (!titleA) continue;

        var href = titleA.attr("href") || "";
        var name = cleanText(titleA.text() || titleA.attr("title"));
        if (!name || name === "阅读本书") continue;

        pushItem(result, seen, name, href, extractCover(item), "");
    }

    return result;
}

function parseCategoryItems(doc) {
    var result = [];
    var seen = {};
    var units = doc.select(".CGsectionTwo-right-content-unit");

    for (var i = 0; i < units.size(); i++) {
        var unit = units.get(i);
        var titleA = selFirst(unit, "a.title[href*='/book/'], p a[href*='/book/']");
        if (!titleA) continue;

        var name = cleanText(titleA.text());
        if (!name || name === "阅读本书") continue;

        var authorA = selFirst(unit, "a[href*='/writer/']");
        var description = authorA ? "作者: " + cleanText(authorA.text()) : "";
        var paragraphs = unit.select("p");
        if (paragraphs.size() > 2) {
            var summary = cleanText(paragraphs.get(2).text());
            if (summary) description = description ? description + " / " + summary : summary;
        }

        pushItem(result, seen, name, titleA.attr("href"), extractCover(unit), description);
    }

    if (result.length > 0) return result;
    return parseThumbItems(doc);
}

function getCategoryNextPage(doc, pageNum) {
    var nextA = selFirst(doc, "a:matchesOwn(下页), a:matchesOwn(下一页)");
    return nextA ? String(pageNum + 1) : null;
}

function executeCategory(input, page) {
    var pageNum = parseInt(page || "1", 10);
    if (!pageNum || pageNum < 1) pageNum = 1;

    var catUrl = buildCategoryUrl(input, pageNum);
    var doc = null;

    var browser = Engine.newBrowser();
    try {
        doc = browser.launch(catUrl, 12000);
    } catch (e) {
        doc = null;
    }
    try { browser.close(); } catch (e2) {}

    if (!doc) {
        var res = fetchRetry(catUrl);
        if (res && res.ok) doc = res.html();
    }

    if (!doc) {
        return Response.error("Khong tai duoc danh sach truyen");
    }

    return Response.success(parseCategoryItems(doc), getCategoryNextPage(doc, pageNum));
}

function parseSearchItems(doc) {
    var result = [];
    var seen = {};
    var paragraphs = doc.select("p");

    for (var i = 0; i < paragraphs.size(); i++) {
        var p = paragraphs.get(i);
        var bookA = selFirst(p, "a[href*='/book/']");
        if (!bookA) continue;

        var name = cleanText(bookA.text());
        if (!name || name === "阅读本书") continue;

        var authorA = selFirst(p, "a[href*='/writer/']");
        var categoryA = null;
        var links = p.select("a[href]");
        for (var li = 0; li < links.size(); li++) {
            var link = links.get(li);
            var href = resolveUrl(link.attr("href") || "");
            if (CATEGORY_LINK_RE.test(href)) {
                categoryA = link;
                break;
            }
        }

        var description = "";
        if (categoryA) description += "[" + cleanText(categoryA.text()) + "] ";
        if (authorA) description += cleanText(authorA.text());

        pushItem(result, seen, name, bookA.attr("href"), DEFAULT_COVER, description);
    }

    return result;
}

function collectSuggests(doc, currentUrl) {
    var result = [];
    var seen = {};
    var links = doc.select("a[href*='/book/']");
    var current = resolveUrl(currentUrl);

    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var href = resolveUrl(link.attr("href") || "");
        if (!BOOK_RE.test(href) || href === current) continue;

        var name = cleanText(link.text() || link.attr("title"));
        if (!name || name === "阅读本书") continue;

        var key = stripHost(href);
        if (seen[key]) continue;
        seen[key] = true;

        result.push({
            name: cleanText(name),
            link: key,
            host: HOST
        });

        if (result.length >= 12) break;
    }

    return result;
}
