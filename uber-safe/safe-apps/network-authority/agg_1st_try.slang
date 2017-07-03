
aggregate.slang
===============

defcon stitchportDelegation(?Zone, ?Vtag, ?Slice) :-
{
  delegateStitchport($Zone, $Vtag, $Slice).
}.

defcon stitchPolicy() :-
{
  stitch(?LocalSlice, ?Zone, ?Vtag) :- 
    ?SomeAgg: createSliver(?TargetSlice, ?Zone, ?Vtag),  (* ??? *) 
    ?TSA := rootId(?TargetSlice), 
    ?TSA: delegateSlice(?P1, ?TargetSlice, "stitch", _),
    ?P1: delegateStitchport(?Zone, ?Vtag, ?LocalSlice),
  
    ?SA := rootId(?LocalSlice),
    ?SA: delegateSlice(?P2, ?Slice, "stitch", _),
    createSliver(?LocalSlice, ?LocalZone, ?LocalVtag),   (* ??? *)
    ?P2: delegateStitchport(?LocalZone, ?LocalVtag, ?TargetSlice).
}.
