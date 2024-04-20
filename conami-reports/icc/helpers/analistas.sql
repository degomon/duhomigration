SELECT ru.name AS nombreruta,
    ru.cv_ruta_id,
    coalesce(bp.taxid, '-') AS cedula,
    bp.name
FROM cv_ruta ru
    LEFT JOIN ad_user u ON ru.ad_user_id = u.ad_user_id
    LEFT JOIN c_bpartner bp ON u.c_bpartner_id = bp.c_bpartner_id