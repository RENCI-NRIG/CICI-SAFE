defcon delegateProjectMembership(?SubjectId, ?ProjectId, ?Delegatable) :-
  {
    delegateMember($SubjectId, $ProjectId, $Delegatable).
    label("project membership delegation $SubjectId $ProjectId").
  }.

defguard createSlice(?ProjectId) :-
  spec("Guard for SliceAuthority.createSlice"),
  ?PolicyRef := label("sa/standard-slice-operations"),
  {
    link($AnchorSet).
    link($PolicyRef).
    link($BearerRef).
    approveSlice($Subject, $ProjectId)?
  }.



defcon testDefcon() :- {
  hello(a) :- hi(a).
}.
