delete from dialogmotekandidat_endring
where personident in
      (select personident from dialogmotekandidat_endring
                          where uuid = '69d4ace5-9e27-497c-ac59-4371b9768aab')
and arsak = 'LUKKET'
and uuid not in ('731100c0-67f4-447c-9288-ada46fee0db5');
