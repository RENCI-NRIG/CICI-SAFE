Request: stitch local stitchport to a local sliver of a slice
AUTH:

Subject:
  stitch(?Slice, ?Stitchport) :-
    controlsSlice(?Subject, ?Slice),
    ?P: delegateStitchport(?Stitchport, ?Slice),
    sliverOf(?Stitchport, ?TargetSlice),
    controlsSlice(?P, ?TargetSlice).
