package com.bianque.health.engine.domain.model

enum class BodyType(val displayName: String, val description: String) {
    QI_DEFICIENCY("气虚质", "气短乏力，易疲劳"),
    YANG_DEFICIENCY("阳虚质", "畏寒怕冷，手足不温"),
    YIN_DEFICIENCY("阴虚质", "口干咽燥，手足心热"),
    PHLEGM_DAMPNESS("痰湿质", "体型肥胖，腹部肥满"),
    DAMPNESS_HEAT("湿热质", "面垢油光，口苦口干"),
    BLOOD_STASIS("血瘀质", "肤色晦暗，舌质紫暗"),
    QI_STAGNATION("气郁质", "神情抑郁，忧虑脆弱"),
    BALANCED("平和质", "阴阳调和，气血通畅"),
    INHERENT_SPECIAL("特禀质", "先天失常，过敏体质")
}