defcall createProject(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    createProject($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall createSlice(?ServerJVM, ?ServerPrincipal, ?Envs, ?ProjectId) :-
  {
    createSlice($ServerJVM, $ServerPrincipal, $Envs, $ProjectId).
  }.

defcall createSliver(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliceId) :-
  {
    createSliver($ServerJVM, $ServerPrincipal, $Envs, $SliceId).
  }.
