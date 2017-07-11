defetch fetchLink(?Link) :- [?Link].

defguard query(?Ref) :-
  ?Set := fetchLink(?Ref),
  {
    importAll($Set).
    ?Who: speaksFor(?Whom, ?X)?
  }
end
