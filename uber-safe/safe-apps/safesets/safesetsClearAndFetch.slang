defetch fetchLink(?Link) :- [?Link].

defguard safesetsClearAndFetch(?Id) :-
  {
    import($id)~
  },
  _ := fetchLink(?Id)
end
