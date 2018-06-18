defcon loadingWhiteList() :-
  {
    allowLoadingFromIP("152.3.136.26").
    label('white-list'). 
  }.

definit loadingWhiteList().

defcon loadingPolicySet() :-
  {
     approveLoading(?IP) :- allowLoadingFromIP(?IP). 
     label('loading-policy').
  }.

definit loadingPolicySet().

defguard loadingSlangFromIP(?IP) :-
  spec("Guard for slang import"),
  ?PolicyRef := label("loading-policy"),
  ?WhitelistRef := label("white-list"),
  {
    link($PolicyRef).
    link($WhitelistRef).
    approveLoading($IP)?
  }.



