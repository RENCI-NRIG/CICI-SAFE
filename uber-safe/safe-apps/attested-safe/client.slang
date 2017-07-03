defcall createSliver(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliceId) :-
  ?EncryptedEnvs := encryptBearerRef("AES", ?Envs),
  {
    createSliver($ServerJVM, $ServerPrincipal, $EncryptedEnvs, $SliceId).
  }.
