namespace smithy.example

use smithy.waiters#waitable

@waitable(
    A: {
        "documentation": "A",
        "acceptors": [
            {
                "state": "success",
                "matcher": {
                    "input": {
                        "path": "missingA == 'hi'",
                        "comparator": "booleanEquals",
                        "expected": "true"
                    }
                }
            }
        ]
    },
    B: {
        "acceptors": [
            {
                "state": "success",
                "matcher": {
                    "output": {
                        "path": "missingB == 'hey'",
                        "comparator": "booleanEquals",
                        "expected": "true"
                    }
                }
            }
        ]
    }
)
operation A {
    input: AInput,
    output: AOutput,
}

structure AInput {
    foo: String,
}

structure AOutput {
    baz: String,
}
