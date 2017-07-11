import!('extractAttribute').

(* Program to resolve a path sequence starting from a known
 * root reference and accumulate the relavant context for 
 * later validation through slog
 *)
resolveContextIterative(?RootRef, ?Path, ?ImportSeq) :-
  spec('route to helper method with an accumulator'),
  ?PathSeq := toSeq?(?Path), // convert the path to slang sequence
  resolveContextIterativeHelper(?RootRef, ?PathSeq, [?RootRef], ?ImportSeq),
end

(* terminating condition for recursion where ?PathSeq is a leaf;
 * unify ?Accumulator with ?ImportSeq
 *)
resolveContextIterativeHelper(?RootRef, [?Leaf], ?ImportSeq, ?ImportSeq).

resolveContextIterativeHelper(
  ?RootRef,
  [?RootName, ?ChildName|?Tail],
  ?Accumulator,
  ?ImportSeq,
) :-
  spec('
    extract the ?ChildSetRef from the ?RootRef context and 
    loop until ?ChildName is nil; accumulate the ?ChildSetRef
  '),
  resolvePredicateAttr(?RootRef, ?RootName, ?ChildName, ?ChildSetRef),
  resolveContextIterativeHelper(
    ?ChildSetRef,
    [?ChildName|?Tail],
    [?ChildSetRef|?Accumulator],
    ?ImportSeq
  ),
end


(* Evaluate the accumulated context to verify
 * the integrity of the path.
 *)
defguard dns?(?Name, ?Address, ?RootRef) :-
  resolveContextIterative(?RootRef, ?Name, ?ImportRef),
  ?NameAtRoot := head?(?Name),
  ?NameAtLeaf := last?(?Name),
  {
    import!($ImportRef).

    dns(?Name, ?Address) :-
      validateName($NameAtRoot, $NameAtLeaf, ?Name),
      _: aRecord(?Name, ?Address),
    end

    (* Terminating condition for recursion where 
     * the root == leaf and name is nil 
     *)
    validateName($NameAtLeaf, $NameAtLeaf, nil).

    validateName(?Speaker, ?NameAtLeaf, ?Name) if
      domainTerm(?Name), // this constraint is required to 
                         // ensure ?Name is grounded or else
                         // range check will fail
      ?Subject := head?(?Name),
      ?Rest    := tail?(?Name),
      ?Speaker: srn(?Subject, _),
      validateName(?Subject, ?NameAtLeaf, ?Rest),
    end

    dns($Name, ?Address)?
  },
end

defcon issueSRN?(?SetName, ?Speaker, ?Subject) :-
  ?SubjectSetRef := concat?(?Subject, 'Root'),
  "$SetName"{
    $Speaker: srn($Subject, $SubjectSetRef).
  },
end

(*
'.': srn('.', '.Root').
'.': srn('edu', 'eduRoot').
'.': srn('com', 'comRoot').
'.': srn('org', 'orgRoot).

'com': srn('amazon', 'amazonRoot').
*)

defcon addRecord?(?SetName, ?Speaker, ?SRN, ?IpAddress) :-
  "$SetName"{
    $Speaker: aRecord($SRN, $IpAddress).
  },
end


// cs.duke.edu. | . -> edu -> duke -> cs

definit
  issueSRN?('.Root', '.', '.'), // this is a self-certified SRN by '.', which needs to be trusted by the authorizer
  issueSRN?('.Root', '.', 'edu'), issueSRN?('.Root', '.', 'com'), issueSRN?('.Root', '.', 'org'), issueSRN?('.Root', '.', 'gov'),
  issueSRN?('comRoot', 'com', 'amazon'), issueSRN?('comRoot', 'com', 'google'), issueSRN?('comRoot', 'com', 'yahoo'),
  issueSRN?('orgRoot', 'org', 'safeclouds'), issueSRN?('orgRoot', 'org', 'eff'), issueSRN?('orgRoot', 'org', 'code'),
  issueSRN?('eduRoot', 'edu', 'duke'), issueSRN?('eduRoot', 'edu', 'unc'), issueSRN?('eduRoot', 'edu', 'ncstate'),
  issueSRN?('googleRoot', 'google', 'mail'), issueSRN?('googleRoot', 'google', 'play'), issueSRN?('googleRoot', 'google', 'search'),
  issueSRN?('dukeRoot', 'duke', 'cs'), issueSRN?('dukeRoot', 'duke', 'math'), issueSRN?('dukeRoot', 'duke', 'ece'),
  addRecord?('dukeRoot', 'duke', dn"cs.duke.edu", '172.168.0.1'),
  dns?(dn"cs.duke.edu", ?Address, '.Root'),
end

// Output upon execution
(*

  query() :- '.Root', '.Root', '.Root', '.Root', '.Root', 'comRoot', 'comRoot', 'comRoot', 'orgRoot', 'orgRoot', 'orgRoot', 'eduRoot', 'eduRoot', 'eduRoot', 'googleRoot', 'googleRoot', 'googleRoot', 'dukeRoot', 'dukeRoot', 'dukeRoot', 'dukeRoot', {
    $Self: dns(dn"cs.duke.edu", '172.168.0.1').
  }.

*)
