"! ICF HTTP handler for the gCTS Dependency Analyzer.
"!
"! Registration:
"!   Transaction SICF → /sap/bc/zgcts/analyze
"!   Handler class: ZGCTS_ANALYZE_HANDLER
"!
"! Request parameters:
"!   tr        (required) — Transport Request number e.g. GMWK900691
"!   format    (optional) — 'json' (default) | 'csv'
"!   persist   (optional) — '1' or 'true' → save result to ZGCTS_DEP_HISTORY
"!   external  (optional) — '1' or 'true' → include external INFO dependencies (Gap 7)
"!
"! Examples:
"!   GET /sap/bc/zgcts/analyze?tr=GMWK900691
"!   GET /sap/bc/zgcts/analyze?tr=GMWK900691&format=csv
"!   GET /sap/bc/zgcts/analyze?tr=GMWK900691&persist=true&external=true
"!
"! Response:
"!   JSON: Content-Type application/json
"!   CSV:  Content-Type text/csv; attachment; filename="<tr>_analysis.csv"
CLASS zgcts_analyze_handler DEFINITION
  PUBLIC FINAL CREATE PUBLIC.

  PUBLIC SECTION.
    INTERFACES if_http_extension.

  PRIVATE SECTION.
    CONSTANTS:
      c_param_tr       TYPE string VALUE 'tr',
      c_param_format   TYPE string VALUE 'format',
      c_param_persist  TYPE string VALUE 'persist',
      c_param_external TYPE string VALUE 'external',
      c_tr_regex       TYPE string VALUE '[A-Z0-9]{3,4}K[0-9]{6}'.

    METHODS respond
      IMPORTING io_server TYPE REF TO if_http_server
                iv_code   TYPE i
                iv_body   TYPE string
                iv_ct     TYPE string DEFAULT 'application/json; charset=utf-8'.

    METHODS is_truthy
      IMPORTING iv_val        TYPE string
      RETURNING VALUE(rv_yes) TYPE abap_bool.

    METHODS error_json
      IMPORTING iv_msg        TYPE string
      RETURNING VALUE(rv_out) TYPE string.

ENDCLASS.


CLASS zgcts_analyze_handler IMPLEMENTATION.

  METHOD if_http_extension~handle_request.
    " ── Read + validate TR parameter ─────────────────────────────────────────
    DATA(lv_tr)  = to_upper( condense( server->request->get_form_field( c_param_tr ) ) ).

    IF lv_tr IS INITIAL.
      respond( io_server = server iv_code = 400
               iv_body = error_json( 'Missing query parameter: tr' ) ).
      RETURN.
    ENDIF.

    IF NOT matches( val = lv_tr regex = c_tr_regex ).
      respond( io_server = server iv_code = 400
               iv_body = error_json(
                 |Invalid TR format '{ lv_tr }'. Expected: [A-Z0-9]{{3,4}}K[0-9]{{6}}| ) ).
      RETURN.
    ENDIF.

    " ── Read optional parameters ─────────────────────────────────────────────
    DATA(lv_format)   = to_lower( server->request->get_form_field( c_param_format ) ).
    DATA(lv_persist)  = is_truthy( server->request->get_form_field( c_param_persist ) ).
    DATA(lv_external) = is_truthy( server->request->get_form_field( c_param_external ) ).

    IF lv_format IS INITIAL. lv_format = 'json'. ENDIF.

    " ── Run the 4-stage analysis pipeline ────────────────────────────────────
    TRY.
        ZCL_GCTS_TR_ANALYZER=>GV_TR_ID           = lv_tr.
        ZCL_GCTS_TR_ANALYZER=>GV_INCLUDE_EXTERNAL = lv_external.

        DATA(lo_analyzer) = NEW zcl_gcts_tr_analyzer( ).

        " Persist to DB if requested (Gap 8)
        IF lv_persist = abap_true.
          lo_analyzer->persist_result( ).
        ENDIF.

        " Return JSON or CSV
        IF lv_format = 'csv'.
          DATA(lv_csv) = lo_analyzer->to_csv( ).
          DATA(lv_filename) = |{ lv_tr }_analysis.csv|.
          respond( io_server = server
                   iv_code   = 200
                   iv_body   = lv_csv
                   iv_ct     = |text/csv; charset=utf-8; Content-Disposition: attachment; filename="{ lv_filename }"| ).
        ELSE.
          respond( io_server = server iv_code = 200 iv_body = lo_analyzer->to_json( ) ).
        ENDIF.

    CATCH cx_root INTO DATA(lx).
        respond( io_server = server iv_code = 500
                 iv_body = error_json( lx->get_text( ) ) ).
    ENDTRY.
  ENDMETHOD.


  METHOD respond.
    io_server->response->set_status( code = iv_code reason = '' ).
    io_server->response->set_header_field( name = 'Content-Type'                   value = iv_ct ).
    io_server->response->set_header_field( name = 'Access-Control-Allow-Origin'    value = '*' ).
    io_server->response->set_header_field( name = 'X-Content-Type-Options'         value = 'nosniff' ).
    io_server->response->set_cdata( iv_body ).
  ENDMETHOD.


  METHOD is_truthy.
    DATA(lv) = to_lower( condense( iv_val ) ).
    rv_yes = xsdbool( lv = '1' OR lv = 'true' OR lv = 'yes' ).
  ENDMETHOD.


  METHOD error_json.
    rv_out = |{"error":"{ iv_msg }"}|.
  ENDMETHOD.

ENDCLASS.
