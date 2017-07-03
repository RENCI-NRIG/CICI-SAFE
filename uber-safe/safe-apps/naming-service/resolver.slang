

defcon resolve() :- 
  spec('Resolving rules'),
  {
    resolve(?Name, ?O) :- singleComponent(?Name), rootNameserver: A(?Name, ?O).
    resolve(?Name, ?O) :- splitFirst(?Name, ?First, ?Rest), rootNameServer: NS(?First, ?TldNameserver), down(?TldNameserver, ?Rest, ?O).
    down(?Nameserver, ?Subpath, ?O) :- ?Nameserver: A(?Subpath, ?O).
    down(?Nameserver, ?Subpath, ?O) :- splitFirst(?Subpath, ?First, ?Rest), ?Nameserver: NS(?First, ?ChildNameserver), down(?ChildNameServer, ?Rest, ?O).
  }


    resolve(?NameAuthority, ?Name, ?Scid) :- singleComponent(?Name), ?NameAuthority: A(?Name, ?Scid).
    resolve(?NameAuthority, ?Name, ?Scid) :- splitFirst(?Name, ?First, ?Rest), NameAuthority: NS(?First, ?ChildAuthority), resolve(?ChildAuthority, ?Rest, ?Scid).

