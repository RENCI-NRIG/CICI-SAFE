defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/file_owner.pem'). *)

defguard tagAccess() :-
  spec('Authorize a suject to have access for a tag'),
  {
    link('QZUgdxERCEg7PDX8-4mY78ToPDnpplFZMTuETYpACEg').
    delegateTagAccessVerbose(?Delegator, ?Tag, ?Delegate, ?Delegatable) :-
      ?Delegator: delegateTagAccess(?Tag, ?Delegate, ?Delegatable).
   
    delegateTagAccessVerbose(?Delegator, ?Tag, ?Delegate, ?Delegatable)? 
  }.


(* definit postIdSet($1). *)
(* definit postFileLabel($1, $2). *)

