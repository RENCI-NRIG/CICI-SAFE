defetch fetchLink(?Link) :- [?Link].

defguard safesetsFetch(?Id) :-
  _ := fetchLink(?Id)
end
